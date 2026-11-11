package com.smarthealthcare.dto;

import com.smarthealthcare.entity.DoctorSlot;

import java.time.Instant;

public record DoctorSlotResponse(Long id, Long doctorId, Instant startTime, Instant endTime) {

    public static DoctorSlotResponse from(DoctorSlot slot) {
        return new DoctorSlotResponse(slot.getId(), slot.getDoctor().getId(), slot.getStartTime(), slot.getEndTime());
    }
}
