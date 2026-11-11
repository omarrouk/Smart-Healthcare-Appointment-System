package com.smarthealthcare.config;

import com.smarthealthcare.security.DatabaseUserDetailsService;
import com.smarthealthcare.security.SecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, DatabaseUserDetailsService userDetailsService,
                                          PasswordEncoder passwordEncoder, SecurityErrorHandler errors) throws Exception {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return http
                .authenticationProvider(provider)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(basic -> basic.authenticationEntryPoint(errors))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me", "/api/auth/csrf").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/chatbot/messages").hasAnyRole("ADMIN", "DOCTOR", "PATIENT")
                        .requestMatchers(HttpMethod.GET, "/api/prescriptions/me", "/api/prescriptions/{prescriptionId}",
                                "/api/medical-records/me", "/api/medical-records/{recordId}")
                            .hasAnyRole("DOCTOR", "PATIENT")
                        .requestMatchers(HttpMethod.POST, "/api/prescriptions", "/api/medical-records").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/prescriptions/{prescriptionId}",
                                "/api/medical-records/{recordId}").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/doctors/{doctorId}/slots")
                            .hasAnyRole("ADMIN", "PATIENT", "DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/doctors/{doctorId}/slots").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/doctors/{doctorId}/slots/{slotId}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/appointments/me", "/api/appointments/{appointmentId}")
                            .hasAnyRole("PATIENT", "DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/appointments").hasRole("PATIENT")
                        .requestMatchers(HttpMethod.PATCH, "/api/appointments/{appointmentId}/cancel").hasRole("PATIENT")
                        .requestMatchers(HttpMethod.PATCH, "/api/appointments/{appointmentId}/complete").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/doctors/me").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/patients/me").hasRole("PATIENT")
                        .requestMatchers(HttpMethod.PUT, "/api/patients/me").hasRole("PATIENT")
                        .requestMatchers(HttpMethod.GET, "/api/doctors").hasAnyRole("ADMIN", "PATIENT")
                        .requestMatchers(HttpMethod.GET, "/api/doctors/{doctorId}", "/api/patients")
                            .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/doctors", "/api/patients").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/doctors/{doctorId}", "/api/patients/{patientId}")
                            .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/doctors/{doctorId}", "/api/patients/{patientId}")
                            .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/patients/{patientId}")
                            .hasAnyRole("ADMIN", "DOCTOR", "PATIENT")
                        .anyRequest().denyAll())
                .build();
    }
}
