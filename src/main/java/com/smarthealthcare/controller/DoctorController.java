package com.smarthealthcare.controller;

import com.smarthealthcare.dto.DoctorResponse;
import com.smarthealthcare.dto.CreateDoctorRequest;
import com.smarthealthcare.dto.DoctorSummaryResponse;
import com.smarthealthcare.dto.UpdateDoctorRequest;
import com.smarthealthcare.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctors;

    public DoctorController(DoctorService doctors) {
        this.doctors = doctors;
    }

    @GetMapping("/me")
    public DoctorResponse me() {
        return doctors.getMyProfile();
    }

    @GetMapping
    public List<DoctorSummaryResponse> search(@RequestParam(required = false) String specialty) {
        return doctors.search(specialty);
    }

    @GetMapping("/{doctorId}")
    public DoctorResponse getById(@PathVariable Long doctorId) {
        return doctors.getById(doctorId);
    }

    @PostMapping
    public ResponseEntity<DoctorResponse> create(@Valid @RequestBody CreateDoctorRequest request) {
        DoctorResponse doctor = doctors.create(request);
        return ResponseEntity.created(URI.create("/api/doctors/" + doctor.id())).body(doctor);
    }

    @PutMapping("/{doctorId}")
    public DoctorResponse update(@PathVariable Long doctorId, @Valid @RequestBody UpdateDoctorRequest request) {
        return doctors.update(doctorId, request);
    }

    @DeleteMapping("/{doctorId}")
    public ResponseEntity<Void> delete(@PathVariable Long doctorId) {
        doctors.delete(doctorId);
        return ResponseEntity.noContent().build();
    }
}
