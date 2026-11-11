package com.smarthealthcare.security;

import com.smarthealthcare.entity.AppointmentStatus;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.exception.InvalidRequestException;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.exception.ResourceNotFoundException;
import com.smarthealthcare.repository.AppointmentRepository;
import com.smarthealthcare.repository.DoctorRepository;
import com.smarthealthcare.repository.PatientRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClinicalAccessService {

    private final CurrentUser currentUser;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final AppointmentRepository appointments;

    public ClinicalAccessService(CurrentUser currentUser, DoctorRepository doctors,
                                 PatientRepository patients, AppointmentRepository appointments) {
        this.currentUser = currentUser;
        this.doctors = doctors;
        this.patients = patients;
        this.appointments = appointments;
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public ClinicalProfile currentProfile() {
        var user = currentUser.get();
        Long profileId = switch (user.getRole()) {
            case DOCTOR -> doctors.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found")).getId();
            case PATIENT -> patients.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found")).getId();
            case ADMIN -> throw new AccessDeniedException("Access denied");
        };
        return new ClinicalProfile(user.getRole(), profileId);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    public ClinicalContext forAppointment(Long appointmentId) {
        Long doctorId = currentProfile().id();
        var appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        if (!appointment.getSlot().getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Access denied");
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ResourceConflictException("Clinical documents require a completed appointment");
        }
        return new ClinicalContext(doctorId, appointment.getPatient().getId(), appointmentId);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    public ClinicalContext forPatient(Long patientId, Long appointmentId) {
        if (appointmentId != null) {
            ClinicalContext context = forAppointment(appointmentId);
            if (!context.patientId().equals(patientId)) {
                throw new InvalidRequestException("Patient does not match the appointment");
            }
            return context;
        }
        Long doctorId = currentProfile().id();
        if (!patients.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient profile not found");
        }
        if (!appointments.existsByPatientIdAndSlotDoctorIdAndStatus(patientId, doctorId, AppointmentStatus.COMPLETED)) {
            throw new AccessDeniedException("Access denied");
        }
        return new ClinicalContext(doctorId, patientId, null);
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public void requireReader(Long doctorId, Long patientId) {
        ClinicalProfile profile = currentProfile();
        Long ownerId = profile.role() == Role.DOCTOR ? doctorId : patientId;
        if (!ownerId.equals(profile.id())) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @PreAuthorize("hasRole('DOCTOR')")
    public void requireAuthor(Long doctorId) {
        if (!doctorId.equals(currentProfile().id())) {
            throw new AccessDeniedException("Access denied");
        }
    }

    public record ClinicalProfile(Role role, Long id) {
    }

    public record ClinicalContext(Long doctorId, Long patientId, Long appointmentId) {
    }
}
