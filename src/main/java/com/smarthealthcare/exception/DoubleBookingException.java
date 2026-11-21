package com.smarthealthcare.exception;

import java.time.Instant;

public class DoubleBookingException extends ResourceConflictException {

    private final String doctorName;
    private final Instant startTime;

    public DoubleBookingException(String doctorName, Instant startTime) {
        super("Slot is already booked");
        this.doctorName = doctorName;
        this.startTime = startTime;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public Instant getStartTime() {
        return startTime;
    }
}
