package com.smarthealthcare.dto;

import com.smarthealthcare.document.Medicine;
import com.smarthealthcare.document.Prescription;

import java.time.Instant;
import java.util.List;

public record PrescriptionResponse(
        String id,
        Long doctorId,
        Long patientId,
        Long appointmentId,
        List<Medicine> medicines,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public static PrescriptionResponse from(Prescription document) {
        return new PrescriptionResponse(document.getId(),
                document.getDoctorId(),
                document.getPatientId(),
                document.getAppointmentId(),
                document.getMedicines(),
                document.getNotes(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getVersion());
    }

    @Override
    public String toString() {
        return "PrescriptionResponse[id=" + id + ", version=" + version + "]";
    }
}
