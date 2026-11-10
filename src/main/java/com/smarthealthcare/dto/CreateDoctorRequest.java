package com.smarthealthcare.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDoctorRequest(
        @NotNull @Valid UserRegistrationRequest account,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String specialty) {
}
