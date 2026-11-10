package com.smarthealthcare.config;

import com.smarthealthcare.dto.UserRegistrationRequest;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.repository.UserRepository;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final String email;
    private final String password;

    public AdminInitializer(UserRepository users, PasswordEncoder passwordEncoder, Validator validator,
                            @Value("${app.bootstrap.admin.email}") String email,
                            @Value("${app.bootstrap.admin.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }

        var account = new UserRegistrationRequest(email, password);
        if (!validator.validate(account).isEmpty()) {
            throw new IllegalStateException("Invalid bootstrap admin credentials; check the bootstrap configuration");
        }
        if (users.findByEmailIgnoreCase(email.strip()).isPresent()) {
            throw new IllegalStateException("Bootstrap email already belongs to an existing account");
        }

        users.save(new User(email, passwordEncoder.encode(password), Role.ADMIN));
    }
}
