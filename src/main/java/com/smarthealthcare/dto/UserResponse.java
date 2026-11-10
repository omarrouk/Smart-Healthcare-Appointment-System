package com.smarthealthcare.dto;

import com.smarthealthcare.entity.Role;

public record UserResponse(Long id, String email, Role role, boolean enabled) {
}
