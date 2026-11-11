package com.smarthealthcare.repository;

import com.smarthealthcare.document.Prescription;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PrescriptionRepository extends MongoRepository<Prescription, String> {

    List<Prescription> findByPatientIdOrderByCreatedAtDescIdDesc(Long patientId);

    List<Prescription> findByDoctorIdOrderByCreatedAtDescIdDesc(Long doctorId);
}
