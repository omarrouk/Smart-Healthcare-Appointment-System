package com.smarthealthcare.repository;

import com.smarthealthcare.entity.DoctorSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface DoctorSlotRepository extends JpaRepository<DoctorSlot, Long> {

    List<DoctorSlot> findByDoctorIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
            Long doctorId, Instant startTime);

    void deleteByDoctorId(Long doctorId);

    @Query("""
            select s from DoctorSlot s
            where s.doctor.id = :doctorId and s.doctor.user.enabled = true and s.startTime > :now
              and not exists (select a.id from Appointment a where a.slot = s
                  and a.status <> com.smarthealthcare.entity.AppointmentStatus.CANCELLED)
            order by s.startTime
            """)
    List<DoctorSlot> findAvailableByDoctorId(Long doctorId, Instant now);
}
