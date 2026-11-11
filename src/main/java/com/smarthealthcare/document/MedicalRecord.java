package com.smarthealthcare.document;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "medical_records")
@CompoundIndexes({
        @CompoundIndex(name = "idx_medical_records_patient_history", def = "{'patientId': 1, 'createdAt': -1, '_id': -1}"),
        @CompoundIndex(name = "idx_medical_records_doctor_history", def = "{'doctorId': 1, 'createdAt': -1, '_id': -1}")
})
public class MedicalRecord {

    @Id
    private String id;

    private Long doctorId;

    private Long patientId;

    private Long appointmentId;

    private List<Medicine> medicines;

    private String notes;

    private List<String> labResults;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    protected MedicalRecord() {
    }

    public MedicalRecord(Long doctorId, Long patientId, Long appointmentId, List<Medicine> medicines,
                        String notes, List<String> labResults) {
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.appointmentId = appointmentId;
        updateDetails(medicines, notes, labResults);
    }

    public void updateDetails(List<Medicine> medicines, String notes, List<String> labResults) {
        this.medicines = List.copyOf(medicines);
        this.notes = notes == null ? null : notes.strip();
        this.labResults = List.copyOf(labResults);
    }

    public String getId() {
        return id;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public List<Medicine> getMedicines() {
        return medicines;
    }

    public String getNotes() {
        return notes;
    }

    public List<String> getLabResults() {
        return labResults;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
