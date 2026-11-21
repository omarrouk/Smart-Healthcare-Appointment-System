package com.smarthealthcare.dto;

import com.smarthealthcare.entity.Role;

public record ChatbotResponse(Role role, String reply, String model) {

    public ChatbotResponse(Role role, String reply) {
        this(role, reply, "stub");
    }

    @Override
    public String toString() {
        return "ChatbotResponse[role=" + role + "]";
    }
}
