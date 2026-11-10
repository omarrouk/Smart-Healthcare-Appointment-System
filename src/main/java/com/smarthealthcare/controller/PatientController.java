package com.smarthealthcare.controller;

import com.smarthealthcare.dto.PatientResponse;
import com.smarthealthcare.service.PatientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
