package com.smarthealthcare.repository;

import com.smarthealthcare.entity.Appointment;
import com.smarthealthcare.entity.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @EntityGraph(attributePaths = {"slot.doctor", "patient"})
    List<Appointment> findByPatientIdOrderBySlotStartTimeAsc(Long patientId);

    @EntityGraph(attributePaths = {"slot.doctor", "patient"})
    List<Appointment> findBySlotDoctorIdOrderBySlotStartTimeAsc(Long doctorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :appointmentId")
    Optional<Appointment> findLockedById(Long appointmentId);

    boolean existsBySlotIdAndStatusNot(Long slotId, AppointmentStatus status);

    boolean existsBySlotId(Long slotId);

    boolean existsByPatientIdAndSlotDoctorUserIdAndStatusNot(
            Long patientId, Long userId, AppointmentStatus status);

    boolean existsBySlotDoctorId(Long doctorId);

    boolean existsByPatientId(Long patientId);

    boolean existsByPatientIdAndSlotDoctorIdAndStatus(Long patientId, Long doctorId, AppointmentStatus status);
}
