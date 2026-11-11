package com.smarthealthcare.repository;

import com.smarthealthcare.document.MedicalRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MedicalRecordRepository extends MongoRepository<MedicalRecord, String> {

    List<MedicalRecord> findByPatientIdOrderByCreatedAtDescIdDesc(Long patientId);

    List<MedicalRecord> findByDoctorIdOrderByCreatedAtDescIdDesc(Long doctorId);
}
