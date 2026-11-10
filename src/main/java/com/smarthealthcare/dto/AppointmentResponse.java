package com.smarthealthcare.dto;

import com.smarthealthcare.entity.AppointmentStatus;

import java.time.Instant;

public record AppointmentResponse(
        Long id,
        Long slotId,
        Long doctorId,
        Long patientId,
        Instant startTime,
        Instant endTime,
        AppointmentStatus status,
        Instant createdAt) {
}
