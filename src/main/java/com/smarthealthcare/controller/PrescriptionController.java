package com.smarthealthcare.controller;

import com.smarthealthcare.dto.CreatePrescriptionRequest;
import com.smarthealthcare.dto.PrescriptionResponse;
import com.smarthealthcare.dto.UpdatePrescriptionRequest;
import com.smarthealthcare.service.PrescriptionService;
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
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptions;

    public PrescriptionController(PrescriptionService prescriptions) {
        this.prescriptions = prescriptions;
    }

    @PostMapping
    public ResponseEntity<PrescriptionResponse> create(@Valid @RequestBody CreatePrescriptionRequest request) {
        var document = prescriptions.create(request);
        return ResponseEntity.created(URI.create("/api/prescriptions/" + document.id())).body(document);
    }

    @GetMapping("/me")
    public List<PrescriptionResponse> mine() {
        return prescriptions.getMine();
    }

    @GetMapping("/{prescriptionId}")
    public PrescriptionResponse getById(@PathVariable String prescriptionId) {
        return prescriptions.getById(prescriptionId);
    }

    @PutMapping("/{prescriptionId}")
    public PrescriptionResponse update(@PathVariable String prescriptionId,
                                          @Valid @RequestBody UpdatePrescriptionRequest request) {
        return prescriptions.update(prescriptionId, request);
    }
}
