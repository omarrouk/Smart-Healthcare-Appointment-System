package com.smarthealthcare.controller;

import com.smarthealthcare.chatbot.ChatbotService;
import com.smarthealthcare.dto.ChatbotRequest;
import com.smarthealthcare.dto.ChatbotResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbot;

    public ChatbotController(ChatbotService chatbot) {
        this.chatbot = chatbot;
    }

    @PostMapping("/messages")
    public ChatbotResponse reply(@Valid @RequestBody ChatbotRequest request) {
        return chatbot.reply(request);
    }
}
