package com.smarthealthcare.security;

import com.smarthealthcare.entity.AppointmentStatus;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.repository.AppointmentRepository;
import com.smarthealthcare.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("accessControl")
public class AccessControlService {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final AppointmentRepository appointments;

    public AccessControlService(CurrentUser currentUser, PatientRepository patients,
                                AppointmentRepository appointments) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.appointments = appointments;
    }

    @Transactional(readOnly = true)
    public boolean canReadPatient(Long patientId) {
        User user = currentUser.get();
        return switch (user.getRole()) {
            case ADMIN -> true;
            case PATIENT -> patients.existsByIdAndUserId(patientId, user.getId());
            case DOCTOR -> appointments.existsByPatientIdAndSlotDoctorUserIdAndStatusNot(
                    patientId, user.getId(), AppointmentStatus.CANCELLED);
        };
    }
}
