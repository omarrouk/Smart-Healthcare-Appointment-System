package com.smarthealthcare.dto;

public record DoctorResponse(Long id, UserResponse account, String name, String specialty) {
}
