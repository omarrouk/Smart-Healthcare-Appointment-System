package com.smarthealthcare.repository;

import com.smarthealthcare.entity.DoctorSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DoctorSlotRepository extends JpaRepository<DoctorSlot, Long> {

    List<DoctorSlot> findByDoctorIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
            Long doctorId, Instant startTime);

    void deleteByDoctorId(Long doctorId);
}
