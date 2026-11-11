package com.smarthealthcare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDoctorRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String specialty) {
}
