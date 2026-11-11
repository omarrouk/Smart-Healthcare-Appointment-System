package com.smarthealthcare.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateDoctorSlotRequest(@NotNull @Future Instant startTime) {
}
