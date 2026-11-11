package com.smarthealthcare.controller;

import com.smarthealthcare.dto.CreateMedicalRecordRequest;
import com.smarthealthcare.dto.MedicalRecordResponse;
import com.smarthealthcare.dto.UpdateMedicalRecordRequest;
import com.smarthealthcare.service.MedicalRecordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/medical-records")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecords;

    public MedicalRecordController(MedicalRecordService medicalRecords) {
        this.medicalRecords = medicalRecords;
    }

    @PostMapping
    public ResponseEntity<MedicalRecordResponse> create(@Valid @RequestBody CreateMedicalRecordRequest request) {
        var document = medicalRecords.create(request);
        return ResponseEntity.created(URI.create("/api/medical-records/" + document.id())).body(document);
    }

    @GetMapping("/me")
    public List<MedicalRecordResponse> mine() {
        return medicalRecords.getMine();
    }

    @GetMapping("/{recordId}")
    public MedicalRecordResponse getById(@PathVariable String recordId) {
        return medicalRecords.getById(recordId);
    }

    @PutMapping("/{recordId}")
    public MedicalRecordResponse update(@PathVariable String recordId,
                                          @Valid @RequestBody UpdateMedicalRecordRequest request) {
        return medicalRecords.update(recordId, request);
    }
}
