package com.smarthealthcare.config;

import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD = "test-only-password";
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @Mock
    private UserRepository users;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void createsAdministratorWithBcryptHash() {
        initializer(PASSWORD).run(new DefaultApplicationArguments());

        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User admin = saved.getValue();
        assertThat(admin.getEmail()).isEqualTo(EMAIL);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
    }

    @Test
    void doesNotResetExistingAdministrator() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        initializer(PASSWORD).run(new DefaultApplicationArguments());

        verify(users, never()).save(any(User.class));
    }

    @Test
    void doesNotPromoteAnExistingAccount() {
        var patient = new User(EMAIL, passwordEncoder.encode(PASSWORD), Role.PATIENT);
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> initializer(PASSWORD).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap email already belongs to an existing account");
        assertThat(patient.getRole()).isEqualTo(Role.PATIENT);
        verify(users, never()).save(any(User.class));
    }

    @Test
    void rejectsMissingPassword() {
        assertThatThrownBy(() -> initializer("").run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid bootstrap admin credentials; check the bootstrap configuration");
        verify(users, never()).save(any(User.class));
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimit() {
        assertThatThrownBy(() -> initializer("é".repeat(40)).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any(User.class));
    }

    private AdminInitializer initializer(String password) {
        return new AdminInitializer(users, passwordEncoder, validator, EMAIL, password);
    }
}
