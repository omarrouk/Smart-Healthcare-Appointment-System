package com.smarthealthcare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePatientRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 30) String phone) {
}
