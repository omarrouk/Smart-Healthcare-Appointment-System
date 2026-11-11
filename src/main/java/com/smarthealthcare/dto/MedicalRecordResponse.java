package com.smarthealthcare.dto;

import com.smarthealthcare.document.Medicine;
import com.smarthealthcare.document.MedicalRecord;

import java.time.Instant;
import java.util.List;

public record MedicalRecordResponse(
        String id,
        Long doctorId,
        Long patientId,
        Long appointmentId,
        List<Medicine> medicines,
        String notes,
        List<String> labResults,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public static MedicalRecordResponse from(MedicalRecord document) {
        return new MedicalRecordResponse(document.getId(),
                document.getDoctorId(),
                document.getPatientId(),
                document.getAppointmentId(),
                document.getMedicines(),
                document.getNotes(),
                document.getLabResults(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getVersion());
    }

    @Override
    public String toString() {
        return "MedicalRecordResponse[id=" + id + ", version=" + version + "]";
    }
}
