package com.smarthealthcare.chatbotservice;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class ChatController {
    private final ModelChatService chatbot;
    private final byte[] serviceKey;

    public ChatController(ModelChatService chatbot, @Value("${chatbot.service-key}") String key) {
        if (key.isBlank()) throw new IllegalArgumentException("CHATBOT_SERVICE_KEY must be configured");
        this.chatbot = chatbot;
        serviceKey = key.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/internal/chatbot/messages")
    public ChatResponse reply(@RequestHeader(value = "X-Chatbot-Service-Key", required = false) String key,
                              @Valid @RequestBody ChatRequest request) {
        if (key == null || !MessageDigest.isEqual(serviceKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return chatbot.reply(request);
    }
}
