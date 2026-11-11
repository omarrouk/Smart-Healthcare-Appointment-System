package com.smarthealthcare.dto;

import com.smarthealthcare.entity.AppointmentStatus;
import com.smarthealthcare.entity.Appointment;

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

    public static AppointmentResponse from(Appointment appointment) {
        var slot = appointment.getSlot();
        return new AppointmentResponse(appointment.getId(), slot.getId(), slot.getDoctor().getId(),
                appointment.getPatient().getId(), slot.getStartTime(), slot.getEndTime(),
                appointment.getStatus(), appointment.getCreatedAt());
    }
}
