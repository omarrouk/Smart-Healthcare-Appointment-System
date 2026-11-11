package com.smarthealthcare.controller;

import com.smarthealthcare.dto.PatientResponse;
import com.smarthealthcare.dto.CreatePatientRequest;
import com.smarthealthcare.dto.UpdatePatientRequest;
import com.smarthealthcare.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patients;

    public PatientController(PatientService patients) {
        this.patients = patients;
    }

    @GetMapping("/me")
    public PatientResponse me() {
        return patients.getMyProfile();
    }

    @GetMapping("/{patientId}")
    public PatientResponse getById(@PathVariable Long patientId) {
        return patients.getById(patientId);
    }

    @GetMapping
    public List<PatientResponse> findAll() {
        return patients.findAll();
    }

    @PostMapping
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
        PatientResponse patient = patients.create(request);
        return ResponseEntity.created(URI.create("/api/patients/" + patient.id())).body(patient);
    }

    @PutMapping("/me")
    public PatientResponse updateMe(@Valid @RequestBody UpdatePatientRequest request) {
        return patients.updateMyProfile(request);
    }

    @PutMapping("/{patientId}")
    public PatientResponse update(@PathVariable Long patientId, @Valid @RequestBody UpdatePatientRequest request) {
        return patients.update(patientId, request);
    }

    @DeleteMapping("/{patientId}")
    public ResponseEntity<Void> delete(@PathVariable Long patientId) {
        patients.delete(patientId);
        return ResponseEntity.noContent().build();
    }
}
