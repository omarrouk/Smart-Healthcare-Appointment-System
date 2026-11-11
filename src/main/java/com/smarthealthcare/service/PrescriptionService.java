package com.smarthealthcare.service;

import com.smarthealthcare.document.Prescription;
import com.smarthealthcare.dto.CreatePrescriptionRequest;
import com.smarthealthcare.dto.PrescriptionResponse;
import com.smarthealthcare.dto.UpdatePrescriptionRequest;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.exception.ResourceNotFoundException;
import com.smarthealthcare.repository.PrescriptionRepository;
import com.smarthealthcare.security.ClinicalAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptions;
    private final ClinicalAccessService access;

    public PrescriptionService(PrescriptionRepository prescriptions, ClinicalAccessService access) {
        this.prescriptions = prescriptions;
        this.access = access;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse create(CreatePrescriptionRequest request) {
        var context = access.forAppointment(request.appointmentId());
        var document = new Prescription(context.doctorId(), context.patientId(), context.appointmentId(),
                request.medicines(), request.notes());
        return PrescriptionResponse.from(prescriptions.save(document));
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public List<PrescriptionResponse> getMine() {
        var profile = access.currentProfile();
        List<Prescription> documents = profile.role() == Role.DOCTOR
                ? prescriptions.findByDoctorIdOrderByCreatedAtDescIdDesc(profile.id())
                : prescriptions.findByPatientIdOrderByCreatedAtDescIdDesc(profile.id());
        return documents.stream().map(PrescriptionResponse::from).toList();
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public PrescriptionResponse getById(String prescriptionId) {
        var document = findDocument(prescriptionId);
        access.requireReader(document.getDoctorId(), document.getPatientId());
        return PrescriptionResponse.from(document);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse update(String prescriptionId, UpdatePrescriptionRequest request) {
        var document = findDocument(prescriptionId);
        access.requireAuthor(document.getDoctorId());
        if (!Objects.equals(request.version(), document.getVersion())) {
            throw new ResourceConflictException("Document changed; reload it before updating");
        }
        document.updateDetails(request.medicines(), request.notes());
        return PrescriptionResponse.from(prescriptions.save(document));
    }

    private Prescription findDocument(String prescriptionId) {
        return prescriptions.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));
    }
}
