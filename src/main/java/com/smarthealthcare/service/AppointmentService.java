package com.smarthealthcare.service;

import com.smarthealthcare.dto.AppointmentResponse;
import com.smarthealthcare.dto.BookAppointmentRequest;
import com.smarthealthcare.entity.Appointment;
import com.smarthealthcare.entity.AppointmentStatus;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.exception.ResourceNotFoundException;
import com.smarthealthcare.repository.AppointmentRepository;
import com.smarthealthcare.repository.DoctorRepository;
import com.smarthealthcare.repository.DoctorSlotRepository;
import com.smarthealthcare.repository.PatientRepository;
import com.smarthealthcare.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final DoctorSlotRepository slots;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final CurrentUser currentUser;

    public AppointmentService(AppointmentRepository appointments, DoctorSlotRepository slots,
                              PatientRepository patients, DoctorRepository doctors, CurrentUser currentUser) {
        this.appointments = appointments;
        this.slots = slots;
        this.patients = patients;
        this.doctors = doctors;
        this.currentUser = currentUser;
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public List<AppointmentResponse> getMine() {
        var user = currentUser.get();
        List<Appointment> matches;
        if (user.getRole() == Role.PATIENT) {
            var patient = patients.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
            matches = appointments.findByPatientIdOrderBySlotStartTimeAsc(patient.getId());
        } else {
            var doctor = doctors.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found"));
            matches = appointments.findBySlotDoctorIdOrderBySlotStartTimeAsc(doctor.getId());
        }
        return matches.stream().map(AppointmentResponse::from).toList();
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public AppointmentResponse getById(Long appointmentId) {
        Appointment appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        var user = currentUser.get();
        Long ownerId = user.getRole() == Role.PATIENT
                ? appointment.getPatient().getUser().getId()
                : appointment.getSlot().getDoctor().getUser().getId();
        requireOwner(ownerId, user.getId());
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    @PreAuthorize("hasRole('PATIENT')")
    public AppointmentResponse book(BookAppointmentRequest request) {
        var patient = patients.findByUserId(currentUser.get().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
        var slot = slots.findById(request.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor slot not found"));
        if (!slot.getStartTime().isAfter(Instant.now()) || !slot.getDoctor().getUser().isEnabled()) {
            throw new ResourceConflictException("Slot is no longer available");
        }
        if (appointments.existsBySlotIdAndStatusNot(slot.getId(), AppointmentStatus.CANCELLED)) {
            throw new ResourceConflictException("Slot is already booked");
        }
        // The partial unique index also rejects bookings that race past the availability check.
        return AppointmentResponse.from(appointments.saveAndFlush(new Appointment(slot, patient)));
    }

    @Transactional
    @PreAuthorize("hasRole('PATIENT')")
    public AppointmentResponse cancel(Long appointmentId) {
        Appointment appointment = findLocked(appointmentId);
        requireOwner(appointment.getPatient().getUser().getId(), currentUser.get().getId());
        requireBooked(appointment);
        appointment.cancel();
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    @PreAuthorize("hasRole('DOCTOR')")
    public AppointmentResponse complete(Long appointmentId) {
        Appointment appointment = findLocked(appointmentId);
        requireOwner(appointment.getSlot().getDoctor().getUser().getId(), currentUser.get().getId());
        requireBooked(appointment);
        appointment.complete();
        return AppointmentResponse.from(appointment);
    }

    private Appointment findLocked(Long appointmentId) {
        return appointments.findLockedById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private void requireBooked(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ResourceConflictException("Only booked appointments can change status");
        }
    }

    private void requireOwner(Long ownerId, Long userId) {
        if (!ownerId.equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
