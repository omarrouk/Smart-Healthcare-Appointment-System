package com.smarthealthcare.service;

import com.smarthealthcare.dto.CreateDoctorSlotRequest;
import com.smarthealthcare.dto.DoctorSlotResponse;
import com.smarthealthcare.entity.Doctor;
import com.smarthealthcare.entity.DoctorSlot;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.exception.InvalidRequestException;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.exception.ResourceNotFoundException;
import com.smarthealthcare.repository.AppointmentRepository;
import com.smarthealthcare.repository.DoctorRepository;
import com.smarthealthcare.repository.DoctorSlotRepository;
import com.smarthealthcare.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DoctorSlotService {

    private final DoctorRepository doctors;
    private final DoctorSlotRepository slots;
    private final AppointmentRepository appointments;
    private final CurrentUser currentUser;

    public DoctorSlotService(DoctorRepository doctors, DoctorSlotRepository slots,
                             AppointmentRepository appointments, CurrentUser currentUser) {
        this.doctors = doctors;
        this.slots = slots;
        this.appointments = appointments;
        this.currentUser = currentUser;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PATIENT', 'DOCTOR')")
    public List<DoctorSlotResponse> getAvailable(Long doctorId) {
        Doctor doctor = findDoctor(doctorId);
        var user = currentUser.get();
        if (user.getRole() == Role.DOCTOR && !doctor.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied");
        }
        return slots.findAvailableByDoctorId(doctorId, Instant.now()).stream()
                .map(DoctorSlotResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorSlotResponse create(Long doctorId, CreateDoctorSlotRequest request) {
        Doctor doctor = findDoctor(doctorId);
        Instant start = request.startTime();
        if (start == null || !start.isAfter(Instant.now())) {
            throw new InvalidRequestException("Slot start must be in the future");
        }
        if (start.getNano() != 0 || start.getEpochSecond() % DoctorSlot.DURATION.toSeconds() != 0) {
            throw new InvalidRequestException("Slot start must be on a UTC half-hour boundary");
        }
        return DoctorSlotResponse.from(slots.saveAndFlush(new DoctorSlot(doctor, start)));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long doctorId, Long slotId) {
        var slot = slots.findById(slotId)
                .filter(value -> value.getDoctor().getId().equals(doctorId))
                .orElseThrow(() -> new ResourceNotFoundException("Doctor slot not found"));
        if (appointments.existsBySlotId(slotId)) {
            throw new ResourceConflictException("Slot has appointment history and cannot be deleted");
        }
        slots.delete(slot);
        slots.flush();
    }

    private Doctor findDoctor(Long doctorId) {
        return doctors.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found"));
    }
}
