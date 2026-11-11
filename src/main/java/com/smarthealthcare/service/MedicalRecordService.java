package com.smarthealthcare.service;

import com.smarthealthcare.document.MedicalRecord;
import com.smarthealthcare.dto.CreateMedicalRecordRequest;
import com.smarthealthcare.dto.MedicalRecordResponse;
import com.smarthealthcare.dto.UpdateMedicalRecordRequest;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.exception.ResourceNotFoundException;
import com.smarthealthcare.repository.MedicalRecordRepository;
import com.smarthealthcare.security.ClinicalAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecords;
    private final ClinicalAccessService access;

    public MedicalRecordService(MedicalRecordRepository medicalRecords, ClinicalAccessService access) {
        this.medicalRecords = medicalRecords;
        this.access = access;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    public MedicalRecordResponse create(CreateMedicalRecordRequest request) {
        var context = access.forPatient(request.patientId(), request.appointmentId());
        var document = new MedicalRecord(context.doctorId(), context.patientId(), context.appointmentId(),
                request.medicines(), request.notes(), request.labResults());
        return MedicalRecordResponse.from(medicalRecords.save(document));
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public List<MedicalRecordResponse> getMine() {
        var profile = access.currentProfile();
        List<MedicalRecord> documents = profile.role() == Role.DOCTOR
                ? medicalRecords.findByDoctorIdOrderByCreatedAtDescIdDesc(profile.id())
                : medicalRecords.findByPatientIdOrderByCreatedAtDescIdDesc(profile.id());
        return documents.stream().map(MedicalRecordResponse::from).toList();
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public MedicalRecordResponse getById(String recordId) {
        var document = findDocument(recordId);
        access.requireReader(document.getDoctorId(), document.getPatientId());
        return MedicalRecordResponse.from(document);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    public MedicalRecordResponse update(String recordId, UpdateMedicalRecordRequest request) {
        var document = findDocument(recordId);
        access.requireAuthor(document.getDoctorId());
        if (!Objects.equals(request.version(), document.getVersion())) {
            throw new ResourceConflictException("Document changed; reload it before updating");
        }
        document.updateDetails(request.medicines(), request.notes(), request.labResults());
        return MedicalRecordResponse.from(medicalRecords.save(document));
    }

    private MedicalRecord findDocument(String recordId) {
        return medicalRecords.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical record not found"));
    }
}
