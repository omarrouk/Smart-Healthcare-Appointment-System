package com.smarthealthcare.chatbotservice;

public record ChatResponse(ChatRequest.Role role, String reply, String model) {
    @Override
    public String toString() {
        return "ChatResponse[role=" + role + ", model=" + model + "]";
    }
}
