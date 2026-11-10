package com.smarthealthcare.security;

import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

    @Mock
    private UserRepository users;

    private DatabaseUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseUserDetailsService(users);
    }

    @Test
    void loadsStoredHashAndRoleAuthority() {
        var user = new User("doctor@example.com",
                new BCryptPasswordEncoder().encode("test-only-password"), Role.DOCTOR);
        when(users.findByEmailIgnoreCase("DOCTOR@example.com")).thenReturn(Optional.of(user));

        var details = service.loadUserByUsername("  DOCTOR@example.com  ");

        assertThat(details.getUsername()).isEqualTo("doctor@example.com");
        assertThat(details.getPassword()).isEqualTo(user.getPasswordHash());
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_DOCTOR");
    }

    @Test
    void preservesDisabledAccountState() {
        var user = new User("patient@example.com",
                new BCryptPasswordEncoder().encode("test-only-password"), Role.PATIENT);
        user.setEnabled(false);
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername(user.getEmail()).isEnabled()).isFalse();
    }

    @Test
    void rejectsUnknownAccount() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
