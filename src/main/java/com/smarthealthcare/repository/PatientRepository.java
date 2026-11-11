package com.smarthealthcare.repository;

import com.smarthealthcare.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByUserId(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "user")
    List<Patient> findAllByOrderByIdAsc();
}
