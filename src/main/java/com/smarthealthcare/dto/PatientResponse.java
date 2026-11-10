package com.smarthealthcare.dto;

public record PatientResponse(Long id, UserResponse account, String name, String phone) {
}
