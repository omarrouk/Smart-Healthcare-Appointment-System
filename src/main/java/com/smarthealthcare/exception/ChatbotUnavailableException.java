package com.smarthealthcare.exception;

public class ChatbotUnavailableException extends RuntimeException {

    public ChatbotUnavailableException() {
        super("Chatbot service is temporarily unavailable");
    }
}
