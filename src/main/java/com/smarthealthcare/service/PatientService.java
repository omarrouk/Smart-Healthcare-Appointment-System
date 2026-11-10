package com.smarthealthcare.service;

import com.smarthealthcare.dto.PatientResponse;
import com.smarthealthcare.dto.UserResponse;
import com.smarthealthcare.entity.Patient;
import com.smarthealthcare.exception.ResourceNotFoundException;
import com.smarthealthcare.repository.PatientRepository;
import com.smarthealthcare.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patients;
    private final CurrentUser currentUser;

    public PatientService(PatientRepository patients, CurrentUser currentUser) {
        this.patients = patients;
        this.currentUser = currentUser;
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

    private PatientResponse toResponse(Patient patient) {
        return new PatientResponse(patient.getId(), UserResponse.from(patient.getUser()),
                patient.getName(), patient.getPhone());
    }
}
