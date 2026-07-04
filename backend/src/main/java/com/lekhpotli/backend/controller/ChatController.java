package com.lekhpotli.backend.controller;

import com.lekhpotli.backend.dto.ChatRequest;
import com.lekhpotli.backend.service.ClaudeChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ClaudeChatService claudeChatService;

    public ChatController(ClaudeChatService claudeChatService) {
        this.claudeChatService = claudeChatService;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        claudeChatService.streamChat(request.messages(), emitter);
        return emitter;
    }
}
