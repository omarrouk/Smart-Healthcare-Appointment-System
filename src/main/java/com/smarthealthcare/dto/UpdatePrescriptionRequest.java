package com.smarthealthcare.dto;

import com.smarthealthcare.document.Medicine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record UpdatePrescriptionRequest(
        @NotNull @PositiveOrZero Long version,
        @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Medicine> medicines,
        @Size(max = 4000) String notes) {

    @Override
    public String toString() {
        return "UpdatePrescriptionRequest[redacted]";
    }
}
