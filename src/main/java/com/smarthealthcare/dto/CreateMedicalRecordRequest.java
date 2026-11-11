package com.smarthealthcare.dto;

import com.smarthealthcare.document.Medicine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateMedicalRecordRequest(
        @NotNull @Positive Long patientId,
        @Positive Long appointmentId,
        @NotNull @Size(max = 20) List<@NotNull @Valid Medicine> medicines,
        @NotBlank @Size(max = 4000) String notes,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> labResults) {

    @Override
    public String toString() {
        return "CreateMedicalRecordRequest[redacted]";
    }
}
