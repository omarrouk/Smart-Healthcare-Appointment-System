package com.smarthealthcare.chatbotservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatRequest(@NotNull Role role, @NotBlank @Size(max = 1000) String message) {
    public enum Role { ADMIN, DOCTOR, PATIENT }

    @Override
    public String toString() {
        return "ChatRequest[role=" + role + "]";
    }
}
