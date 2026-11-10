package com.smarthealthcare.service;

import com.smarthealthcare.dto.DoctorResponse;
import com.smarthealthcare.dto.UserResponse;
import com.smarthealthcare.exception.ResourceNotFoundException;
import com.smarthealthcare.repository.DoctorRepository;
import com.smarthealthcare.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DoctorService {

    private final DoctorRepository doctors;
    private final CurrentUser currentUser;

    public DoctorService(DoctorRepository doctors, CurrentUser currentUser) {
        this.doctors = doctors;
        this.currentUser = currentUser;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    public DoctorResponse getMyProfile() {
        var doctor = doctors.findByUserId(currentUser.get().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found"));

        return new DoctorResponse(doctor.getId(), UserResponse.from(doctor.getUser()),
                doctor.getName(), doctor.getSpecialty());
    }
}
