package com.smarthealthcare.dto;

import com.smarthealthcare.entity.Role;

public record ChatbotResponse(Role role, String reply) {

    @Override
    public String toString() {
        return "ChatbotResponse[role=" + role + "]";
    }
}
