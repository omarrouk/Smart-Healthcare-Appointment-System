package com.smarthealthcare.controller;

import com.smarthealthcare.dto.DoctorResponse;
import com.smarthealthcare.service.DoctorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
