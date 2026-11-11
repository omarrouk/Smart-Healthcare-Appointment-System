package com.smarthealthcare.chatbot;

import com.smarthealthcare.dto.ChatbotRequest;
import com.smarthealthcare.dto.ChatbotResponse;

public interface ChatbotService {

    ChatbotResponse reply(ChatbotRequest request);
}
