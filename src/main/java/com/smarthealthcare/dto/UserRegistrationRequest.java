package com.smarthealthcare.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72) String password) {

    @Override
    public String toString() {
        return "UserRegistrationRequest[redacted]";
    }
}
