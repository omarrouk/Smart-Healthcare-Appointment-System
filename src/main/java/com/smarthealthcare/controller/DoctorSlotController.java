package com.smarthealthcare.controller;

import com.smarthealthcare.dto.CreateDoctorSlotRequest;
import com.smarthealthcare.dto.DoctorSlotResponse;
import com.smarthealthcare.service.DoctorSlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/doctors/{doctorId}/slots")
public class DoctorSlotController {

    private final DoctorSlotService slots;

    public DoctorSlotController(DoctorSlotService slots) {
        this.slots = slots;
    }

    @GetMapping
    public List<DoctorSlotResponse> available(@PathVariable Long doctorId) {
        return slots.getAvailable(doctorId);
    }

    @PostMapping
    public ResponseEntity<DoctorSlotResponse> create(@PathVariable Long doctorId,
                                                    @Valid @RequestBody CreateDoctorSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slots.create(doctorId, request));
    }

    @DeleteMapping("/{slotId}")
    public ResponseEntity<Void> delete(@PathVariable Long doctorId, @PathVariable Long slotId) {
        slots.delete(doctorId, slotId);
        return ResponseEntity.noContent().build();
    }
}
