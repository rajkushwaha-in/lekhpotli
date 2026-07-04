package com.lekhpotli.backend.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.WebSearchTool20260209;
import com.anthropic.errors.UnauthorizedException;
import com.lekhpotli.backend.dto.ChatMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ClaudeChatService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeChatService.class);

    // Bounds how many times we'll resume a single turn on stop_reason=pause_turn
    // (the API's internal server-tool iteration cap - e.g. a question needing many
    // web searches). Caps worst-case latency/cost per user message.
    private static final int MAX_PAUSE_CONTINUATIONS = 3;

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;
    private final String systemPrompt;
    private final long webSearchMaxUses;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ClaudeChatService(
            AnthropicClient client,
            @Value("${anthropic.model}") String model,
            @Value("${anthropic.max-tokens}") long maxTokens,
            @Value("${anthropic.system-prompt}") String systemPrompt,
            @Value("${anthropic.web-search-max-uses}") long webSearchMaxUses) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
        this.systemPrompt = systemPrompt;
        this.webSearchMaxUses = webSearchMaxUses;
    }

    public void streamChat(List<ChatMessageDto> history, SseEmitter emitter) {
        executor.submit(() -> {
            try {
                List<MessageParam> messages = new ArrayList<>();
                for (ChatMessageDto message : history) {
                    MessageParam.Role role = "user".equals(message.role())
                            ? MessageParam.Role.USER
                            : MessageParam.Role.ASSISTANT;
                    messages.add(MessageParam.builder().role(role).content(message.content()).build());
                }

                StopReason stopReason;
                int continuations = 0;
                do {
                    MessageCreateParams params = MessageCreateParams.builder()
                            .model(Model.of(model))
                            .maxTokens(maxTokens)
                            .system(systemPrompt)
                            // Lets Claude search the web for anything past its training
                            // cutoff or otherwise not in the conversation (current events,
                            // scores, prices, etc.) instead of answering from stale knowledge.
                            // allowedCallers=DIRECT is required for Haiku-tier models, which
                            // don't support the programmatic (code-execution-driven) calling
                            // path this tool defaults to; it also works fine on larger models,
                            // so it's set unconditionally regardless of which model is configured.
                            // maxUses caps searches-per-turn as a cost/abuse guard.
                            .addTool(WebSearchTool20260209.builder()
                                    .maxUses(webSearchMaxUses)
                                    .addAllowedCaller(WebSearchTool20260209.AllowedCaller.DIRECT)
                                    .build())
                            .messages(messages)
                            .build();

                    MessageAccumulator accumulator = MessageAccumulator.create();
                    try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
                        stream.stream().forEach(event -> {
                            accumulator.accumulate(event);
                            event.contentBlockDelta().ifPresent(delta ->
                                    delta.delta().text().ifPresent(textDelta -> {
                                        try {
                                            emitter.send(SseEmitter.event().name("delta").data(textDelta.text()));
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    }));
                        });
                    }

                    Message response = accumulator.message();
                    stopReason = response.stopReason().orElse(StopReason.END_TURN);
                    // pause_turn means the server's tool loop (e.g. a chain of web
                    // searches) hit its internal cap mid-turn, not that Claude is done -
                    // resend the accumulated response as-is to let it continue.
                    if (stopReason == StopReason.PAUSE_TURN) {
                        messages.add(response.toParam());
                        continuations++;
                    }
                } while (stopReason == StopReason.PAUSE_TURN && continuations < MAX_PAUSE_CONTINUATIONS);

                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                log.error("Claude streaming failed", e);
                String userMessage = e instanceof UnauthorizedException
                        ? "Invalid Anthropic API key. Add a real key to backend/.env and restart the server."
                        : "Sorry, something went wrong talking to Claude. Please try again.";
                try {
                    emitter.send(SseEmitter.event().name("error").data(userMessage));
                    // We've already told the client what happened via the "error" event above,
                    // so complete the stream normally here — completeWithError() re-triggers
                    // Spring's exception resolution, which tries to write a second (incompatible)
                    // error body onto an SSE response whose headers are already committed.
                    emitter.complete();
                } catch (Exception sendFailure) {
                    // Emitter already closed/broken — nothing more we can do.
                    emitter.completeWithError(sendFailure);
                }
            }
        });
    }
}
