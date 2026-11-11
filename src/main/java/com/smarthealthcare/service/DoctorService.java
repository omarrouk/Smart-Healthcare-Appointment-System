package com.smarthealthcare.service;

import com.smarthealthcare.dto.DoctorResponse;
import com.smarthealthcare.dto.CreateDoctorRequest;
import com.smarthealthcare.dto.DoctorSummaryResponse;
import com.smarthealthcare.dto.UpdateDoctorRequest;
import com.smarthealthcare.dto.UserResponse;
import com.smarthealthcare.entity.Doctor;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.exception.InvalidRequestException;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.exception.ResourceNotFoundException;
import com.smarthealthcare.repository.DoctorRepository;
import com.smarthealthcare.repository.AppointmentRepository;
import com.smarthealthcare.repository.DoctorSlotRepository;
import com.smarthealthcare.repository.UserRepository;
import com.smarthealthcare.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DoctorService {

    private final DoctorRepository doctors;
    private final CurrentUser currentUser;
    private final UserAccountService accounts;
    private final UserRepository users;
    private final DoctorSlotRepository slots;
    private final AppointmentRepository appointments;

    public DoctorService(DoctorRepository doctors, CurrentUser currentUser, UserAccountService accounts,
                         UserRepository users, DoctorSlotRepository slots, AppointmentRepository appointments) {
        this.doctors = doctors;
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.users = users;
        this.slots = slots;
        this.appointments = appointments;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    public DoctorResponse getMyProfile() {
        var doctor = doctors.findByUserId(currentUser.get().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found"));

        return toResponse(doctor);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PATIENT')")
    public List<DoctorSummaryResponse> search(String specialty) {
        if (specialty != null && (specialty.isBlank() || specialty.length() > 100)) {
            throw new InvalidRequestException("Specialty must contain between 1 and 100 characters");
        }
        List<Doctor> matches = specialty == null
                ? doctors.findAll(Sort.by("name", "id"))
                : doctors.findBySpecialtyIgnoreCase(specialty.strip());
        return matches.stream()
                .map(doctor -> new DoctorSummaryResponse(doctor.getId(), doctor.getName(), doctor.getSpecialty()))
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public DoctorResponse getById(Long doctorId) {
        return toResponse(findDoctor(doctorId));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorResponse create(CreateDoctorRequest request) {
        var account = accounts.create(request.account(), Role.DOCTOR);
        return toResponse(doctors.save(new Doctor(account, request.name(), request.specialty())));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorResponse update(Long doctorId, UpdateDoctorRequest request) {
        Doctor doctor = findDoctor(doctorId);
        doctor.updateDetails(request.name(), request.specialty());
        return toResponse(doctor);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long doctorId) {
        Doctor doctor = findDoctor(doctorId);
        if (appointments.existsBySlotDoctorId(doctorId)) {
            throw new ResourceConflictException("Doctor has appointment history and cannot be deleted");
        }
        var account = doctor.getUser();
        slots.deleteByDoctorId(doctorId);
        slots.flush();
        doctors.delete(doctor);
        doctors.flush();
        users.delete(account);
    }

    private Doctor findDoctor(Long doctorId) {
        return doctors.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found"));
    }

    private DoctorResponse toResponse(Doctor doctor) {
        return new DoctorResponse(doctor.getId(), UserResponse.from(doctor.getUser()),
                doctor.getName(), doctor.getSpecialty());
    }
}
