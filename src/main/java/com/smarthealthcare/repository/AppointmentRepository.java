package com.smarthealthcare.repository;

import com.smarthealthcare.entity.Appointment;
import com.smarthealthcare.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatientIdOrderBySlotStartTimeAsc(Long patientId);

    List<Appointment> findBySlotDoctorIdOrderBySlotStartTimeAsc(Long doctorId);

    boolean existsByPatientIdAndSlotDoctorUserIdAndStatusNot(
            Long patientId, Long userId, AppointmentStatus status);

    boolean existsBySlotDoctorId(Long doctorId);

    boolean existsByPatientId(Long patientId);
}
