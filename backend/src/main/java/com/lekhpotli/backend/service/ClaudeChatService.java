package com.lekhpotli.backend.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.errors.UnauthorizedException;
import com.lekhpotli.backend.dto.ChatMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ClaudeChatService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeChatService.class);

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;
    private final String systemPrompt;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ClaudeChatService(
            AnthropicClient client,
            @Value("${anthropic.model}") String model,
            @Value("${anthropic.max-tokens}") long maxTokens,
            @Value("${anthropic.system-prompt}") String systemPrompt) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
        this.systemPrompt = systemPrompt;
    }

    public void streamChat(List<ChatMessageDto> history, SseEmitter emitter) {
        executor.submit(() -> {
            try {
                MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                        .model(Model.of(model))
                        .maxTokens(maxTokens)
                        .system(systemPrompt);

                for (ChatMessageDto message : history) {
                    if ("user".equals(message.role())) {
                        paramsBuilder.addUserMessage(message.content());
                    } else {
                        paramsBuilder.addMessage(MessageParam.builder()
                                .role(MessageParam.Role.ASSISTANT)
                                .content(message.content())
                                .build());
                    }
                }

                try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(paramsBuilder.build())) {
                    stream.stream().forEach(event -> {
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
