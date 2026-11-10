package com.smarthealthcare.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthcare.entity.Appointment;
import com.smarthealthcare.entity.AppointmentStatus;
import com.smarthealthcare.entity.Doctor;
import com.smarthealthcare.entity.DoctorSlot;
import com.smarthealthcare.entity.Patient;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.repository.AppointmentRepository;
import com.smarthealthcare.repository.DoctorRepository;
import com.smarthealthcare.repository.DoctorSlotRepository;
import com.smarthealthcare.repository.PatientRepository;
import com.smarthealthcare.repository.UserRepository;
import com.smarthealthcare.service.PatientService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.bootstrap.admin.enabled=false")
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    private static final String PASSWORD = "test-only-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final UserRepository users;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final DoctorSlotRepository slots;
    private final AppointmentRepository appointments;
    private final PatientService patientService;
    private final EntityManager entityManager;

    private User admin;
    private Doctor doctor;
    private Doctor unrelatedDoctor;
    private Patient patient;
    private Patient otherPatient;
    private Appointment appointment;

    @Autowired
    SecurityIntegrationTest(MockMvc mvc, ObjectMapper mapper, UserRepository users, DoctorRepository doctors,
                            PatientRepository patients, DoctorSlotRepository slots,
                            AppointmentRepository appointments, PatientService patientService,
                            EntityManager entityManager) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.users = users;
        this.doctors = doctors;
        this.patients = patients;
        this.slots = slots;
        this.appointments = appointments;
        this.patientService = patientService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void createAccountsAndProfiles() {
        admin = createUser(Role.ADMIN);
        doctor = doctors.saveAndFlush(new Doctor(createUser(Role.DOCTOR), "Test Doctor", "Cardiology"));
        unrelatedDoctor = doctors.saveAndFlush(new Doctor(createUser(Role.DOCTOR), "Other Doctor", "Neurology"));
        patient = patients.saveAndFlush(new Patient(createUser(Role.PATIENT), "Test Patient", null));
        otherPatient = patients.saveAndFlush(new Patient(createUser(Role.PATIENT), "Other Patient", null));
        DoctorSlot slot = slots.saveAndFlush(new DoctorSlot(doctor, Instant.parse("2030-01-07T10:00:00Z")));
        appointment = appointments.saveAndFlush(new Appointment(slot, patient));
    }

    @Test
    void healthRemainsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void unauthenticatedRequestReturnsJsonChallenge() throws Exception {
        mvc.perform(get("/api/auth/me").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"SmartHealthcare\""))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void authenticatesEachRoleWithoutExposingPassword(Role role) throws Exception {
        User user = switch (role) {
            case ADMIN -> admin;
            case DOCTOR -> doctor.getUser();
            case PATIENT -> patient.getUser();
        };

        mvc.perform(get("/api/auth/me").with(httpBasic(user.getEmail().toUpperCase(Locale.ROOT), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void rejectsWrongPasswordWithoutEchoingCredentials() throws Exception {
        mvc.perform(get("/api/auth/me").with(httpBasic(admin.getEmail(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required or credentials invalid"))
                .andExpect(content().string(not(containsString("wrong-password"))))
                .andExpect(content().string(not(containsString(PASSWORD_HASH))));
    }

    @Test
    void unknownAccountUsesSameAuthenticationError() throws Exception {
        mvc.perform(get("/api/auth/me").with(httpBasic(UUID.randomUUID() + "@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required or credentials invalid"));
    }

    @Test
    void rejectsDisabledAccount() throws Exception {
        admin.setEnabled(false);
        users.saveAndFlush(admin);

        mvc.perform(get("/api/auth/me").with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresCredentialsOnEveryRequest() throws Exception {
        mvc.perform(get("/api/auth/me").with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientMeReturnsOnlyCurrentPatientsProfile() throws Exception {
        mvc.perform(get("/api/patients/me").with(httpBasic(patient.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.getId()))
                .andExpect(jsonPath("$.account.id").value(patient.getUser().getId()))
                .andExpect(jsonPath("$.account.passwordHash").doesNotExist());
    }

    @Test
    void doctorMeReturnsOnlyCurrentDoctorsProfile() throws Exception {
        mvc.perform(get("/api/doctors/me").with(httpBasic(doctor.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doctor.getId()))
                .andExpect(jsonPath("$.account.id").value(doctor.getUser().getId()));
    }

    @Test
    void patientCannotAccessDoctorOnlyRoute() throws Exception {
        mvc.perform(get("/api/doctors/me").with(httpBasic(patient.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void doctorCannotAccessPatientOnlyRoute() throws Exception {
        mvc.perform(get("/api/patients/me").with(httpBasic(doctor.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCanReadOwnProfileById() throws Exception {
        mvc.perform(get("/api/patients/{id}", patient.getId())
                        .with(httpBasic(patient.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.getId()));
    }

    @Test
    void patientCannotReadAnotherPatientsProfile() throws Exception {
        mvc.perform(get("/api/patients/{id}", otherPatient.getId())
                        .with(httpBasic(patient.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(content().string(not(containsString(otherPatient.getName()))));
    }

    @Test
    void administratorCanReadPatientProfile() throws Exception {
        mvc.perform(get("/api/patients/{id}", otherPatient.getId()).with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(otherPatient.getId()));
    }

    @Test
    void doctorCanReadPatientWithAssignedAppointment() throws Exception {
        mvc.perform(get("/api/patients/{id}", patient.getId())
                        .with(httpBasic(doctor.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void unrelatedDoctorCannotReadPatientProfile() throws Exception {
        mvc.perform(get("/api/patients/{id}", patient.getId())
                        .with(httpBasic(unrelatedDoctor.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelledAppointmentDoesNotGrantPatientAccess() throws Exception {
        updateAppointmentStatus(AppointmentStatus.CANCELLED);

        mvc.perform(get("/api/patients/{id}", patient.getId())
                        .with(httpBasic(doctor.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void completedAppointmentPreservesPatientAccess() throws Exception {
        updateAppointmentStatus(AppointmentStatus.COMPLETED);

        mvc.perform(get("/api/patients/{id}", patient.getId())
                        .with(httpBasic(doctor.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void administratorGetsNotFoundForMissingPatient() throws Exception {
        mvc.perform(get("/api/patients/{id}", Long.MAX_VALUE).with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Patient profile not found"));
    }

    @Test
    void invalidPatientIdReturnsConsistentBadRequest() throws Exception {
        mvc.perform(get("/api/patients/not-a-number").with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));
    }

    @Test
    void doctorWithoutProfileGetsNotFound() throws Exception {
        User account = createUser(Role.DOCTOR);

        mvc.perform(get("/api/doctors/me").with(httpBasic(account.getEmail(), PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Doctor profile not found"));
    }

    @Test
    void ownershipIsEnforcedAtServiceBoundary() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                patient.getUser().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            assertThatThrownBy(() -> patientService.getById(otherPatient.getId()))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void csrfEndpointSuppliesTokenAndHttpOnlyCookie() throws Exception {
        mvc.perform(get("/api/auth/csrf").with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().httpOnly("XSRF-TOKEN", true))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void unsafeRequestWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/me").with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF token is missing or invalid"));
    }

    @Test
    void csrfCookieAndReturnedTokenPassCsrfValidationButDoNotGrantRouteAccess() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String token = mapper.readTree(response.getContentAsString()).get("token").asText();
        MockCookie csrfCookie = MockCookie.parse(response.getHeader(HttpHeaders.SET_COOKIE));

        mvc.perform(post("/api/auth/me").with(httpBasic(admin.getEmail(), PASSWORD))
                        .cookie(csrfCookie).header("X-XSRF-TOKEN", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void unmappedRoutesAreDeniedByDefault() throws Exception {
        mvc.perform(get("/api/not-implemented").with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    private User createUser(Role role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", PASSWORD_HASH, role));
    }

    private void updateAppointmentStatus(AppointmentStatus status) {
        entityManager.createNativeQuery("UPDATE appointments SET status = :status WHERE id = :id")
                .setParameter("status", status.name())
                .setParameter("id", appointment.getId())
                .executeUpdate();
        entityManager.clear();
    }
}
