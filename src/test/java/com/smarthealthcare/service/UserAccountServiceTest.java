package com.smarthealthcare.service;

import com.smarthealthcare.dto.UserRegistrationRequest;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserRepository users;

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"DOCTOR", "PATIENT"})
    void createsEnabledClinicalAccountWithNormalizedEmailAndBcryptHash(Role role) {
        var encoder = new BCryptPasswordEncoder();
        var service = new UserAccountService(users, encoder);
        var request = new UserRegistrationRequest("PERSON@example.com", "test-only-password");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = service.create(request, role);

        assertThat(user.getEmail()).isEqualTo("person@example.com");
        assertThat(user.getRole()).isEqualTo(role);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo(request.password());
        assertThat(encoder.matches(request.password(), user.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsAnAlreadyRegisteredEmail() {
        var service = new UserAccountService(users, new BCryptPasswordEncoder());
        when(users.existsByEmailIgnoreCase("person@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new UserRegistrationRequest("PERSON@example.com", "test-only-password"), Role.PATIENT))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Email is already registered");
        verify(users, never()).saveAndFlush(any(User.class));
    }
}
