package com.smarthealthcare.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Medicine(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String dosage,
        @Size(max = 500) String instructions) {

    public Medicine {
        name = name == null ? null : name.strip();
        dosage = dosage == null ? null : dosage.strip();
        instructions = instructions == null ? null : instructions.strip();
    }

    @Override
    public String toString() {
        return "Medicine[redacted]";
    }
}
