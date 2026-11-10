package com.smarthealthcare.dto;

import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;

public record UserResponse(Long id, String email, Role role, boolean enabled) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.isEnabled());
    }
}
