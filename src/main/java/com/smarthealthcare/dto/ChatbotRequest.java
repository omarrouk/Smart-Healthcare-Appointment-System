package com.smarthealthcare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatbotRequest(@NotBlank @Size(max = 1000) String message) {

    @Override
    public String toString() {
        return "ChatbotRequest[redacted]";
    }
}
