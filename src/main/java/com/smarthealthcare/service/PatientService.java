package com.smarthealthcare.service;

import com.smarthealthcare.dto.PatientResponse;
import com.smarthealthcare.dto.CreatePatientRequest;
import com.smarthealthcare.dto.UpdatePatientRequest;
import com.smarthealthcare.dto.UserResponse;
import com.smarthealthcare.entity.Patient;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.exception.ResourceNotFoundException;
import com.smarthealthcare.repository.PatientRepository;
import com.smarthealthcare.repository.AppointmentRepository;
import com.smarthealthcare.repository.UserRepository;
import com.smarthealthcare.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patients;
    private final CurrentUser currentUser;
    private final UserAccountService accounts;
    private final UserRepository users;
    private final AppointmentRepository appointments;

    public PatientService(PatientRepository patients, CurrentUser currentUser, UserAccountService accounts,
                          UserRepository users, AppointmentRepository appointments) {
        this.patients = patients;
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.users = users;
        this.appointments = appointments;
    }

    @PreAuthorize("hasRole('PATIENT')")
    public PatientResponse getMyProfile() {
        return toResponse(patients.findByUserId(currentUser.get().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found")));
    }

    @PreAuthorize("@accessControl.canReadPatient(#patientId)")
    public PatientResponse getById(Long patientId) {
        return toResponse(patients.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<PatientResponse> findAll() {
        return patients.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PatientResponse create(CreatePatientRequest request) {
        var account = accounts.create(request.account(), Role.PATIENT);
        return toResponse(patients.save(new Patient(account, request.name(), request.phone())));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PatientResponse update(Long patientId, UpdatePatientRequest request) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
        patient.updateDetails(request.name(), request.phone());
        return toResponse(patient);
    }

    @Transactional
    @PreAuthorize("hasRole('PATIENT')")
    public PatientResponse updateMyProfile(UpdatePatientRequest request) {
        Patient patient = patients.findByUserId(currentUser.get().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
        patient.updateDetails(request.name(), request.phone());
        return toResponse(patient);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long patientId) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
        if (appointments.existsByPatientId(patientId)) {
            throw new ResourceConflictException("Patient has appointment history and cannot be deleted");
        }
        var account = patient.getUser();
        patients.delete(patient);
        patients.flush();
        users.delete(account);
    }

    private PatientResponse toResponse(Patient patient) {
        return new PatientResponse(patient.getId(), UserResponse.from(patient.getUser()),
                patient.getName(), patient.getPhone());
    }
}
