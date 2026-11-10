package com.smarthealthcare.repository;

import com.smarthealthcare.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatientIdOrderBySlotStartTimeAsc(Long patientId);

    List<Appointment> findBySlotDoctorIdOrderBySlotStartTimeAsc(Long doctorId);
}
