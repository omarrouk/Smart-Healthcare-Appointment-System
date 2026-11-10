package com.smarthealthcare.dto;

import java.time.Instant;

public record DoctorSlotResponse(Long id, Long doctorId, Instant startTime, Instant endTime) {
}
