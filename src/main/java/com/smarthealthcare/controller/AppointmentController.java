package com.smarthealthcare.controller;

import com.smarthealthcare.dto.AppointmentResponse;
import com.smarthealthcare.dto.BookAppointmentRequest;
import com.smarthealthcare.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @GetMapping("/me")
    public List<AppointmentResponse> mine() {
        return appointments.getMine();
    }

    @GetMapping("/{appointmentId}")
    public AppointmentResponse getById(@PathVariable Long appointmentId) {
        return appointments.getById(appointmentId);
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentRequest request) {
        var appointment = appointments.book(request);
        return ResponseEntity.created(URI.create("/api/appointments/" + appointment.id())).body(appointment);
    }

    @PatchMapping("/{appointmentId}/cancel")
    public AppointmentResponse cancel(@PathVariable Long appointmentId) {
        return appointments.cancel(appointmentId);
    }

    @PatchMapping("/{appointmentId}/complete")
    public AppointmentResponse complete(@PathVariable Long appointmentId) {
        return appointments.complete(appointmentId);
    }
}
