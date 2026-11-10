package com.smarthealthcare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "doctor_slots", uniqueConstraints = @UniqueConstraint(
        name = "uq_doctor_slot", columnNames = {"doctor_id", "start_time"}))
public class DoctorSlot {

    public static final Duration DURATION = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false, updatable = false)
    private Doctor doctor;

    @Column(name = "start_time", nullable = false, updatable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false, updatable = false)
    private Instant endTime;

    protected DoctorSlot() {
    }

    public DoctorSlot(Doctor doctor, Instant startTime) {
        if (startTime == null || startTime.getNano() != 0
                || startTime.getEpochSecond() % DURATION.toSeconds() != 0) {
            throw new IllegalArgumentException("Slot start must be on a UTC half-hour boundary");
        }
        this.doctor = doctor;
        this.startTime = startTime;
        this.endTime = startTime.plus(DURATION);
    }

    public Long getId() {
        return id;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }
}
