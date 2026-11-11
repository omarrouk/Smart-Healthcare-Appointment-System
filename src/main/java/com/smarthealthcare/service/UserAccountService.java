package com.smarthealthcare.service;

import com.smarthealthcare.dto.UserRegistrationRequest;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserAccountService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public User create(UserRegistrationRequest request, Role role) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResourceConflictException("Email is already registered");
        }
        return users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()), role));
    }
}
