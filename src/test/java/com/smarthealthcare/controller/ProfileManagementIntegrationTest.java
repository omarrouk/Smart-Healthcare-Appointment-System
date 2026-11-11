package com.smarthealthcare.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smarthealthcare.dto.CreateDoctorRequest;
import com.smarthealthcare.dto.CreatePatientRequest;
import com.smarthealthcare.dto.UpdateDoctorRequest;
import com.smarthealthcare.dto.UpdatePatientRequest;
import com.smarthealthcare.dto.UserRegistrationRequest;
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
import com.smarthealthcare.service.DoctorService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.bootstrap.admin.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ProfileManagementIntegrationTest {

    private static final String PASSWORD = "test-only-password";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final UserRepository users;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final DoctorSlotRepository slots;
    private final AppointmentRepository appointments;
    private final DoctorService doctorService;
    private final EntityManager entityManager;

    private User admin;
    private Doctor doctor;
    private Patient patient;
    private Patient otherPatient;

    @Autowired
    ProfileManagementIntegrationTest(MockMvc mvc, ObjectMapper mapper, UserRepository users,
                                     DoctorRepository doctors, PatientRepository patients,
                                     DoctorSlotRepository slots, AppointmentRepository appointments,
                                     DoctorService doctorService, EntityManager entityManager) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.users = users;
        this.doctors = doctors;
        this.patients = patients;
        this.slots = slots;
        this.appointments = appointments;
        this.doctorService = doctorService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void createFixtures() {
        admin = user(Role.ADMIN);
        doctor = doctors.saveAndFlush(new Doctor(user(Role.DOCTOR), "Test Doctor", "Cardiology-" + UUID.randomUUID()));
        patient = patients.saveAndFlush(new Patient(user(Role.PATIENT), "Test Patient", null));
        otherPatient = patients.saveAndFlush(new Patient(user(Role.PATIENT), "Other Patient", null));
    }

    @Test
    void administratorCreatesDoctorAndNewAccountCanAuthenticate() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var request = new CreateDoctorRequest(new UserRegistrationRequest(email, PASSWORD), " New Doctor ", " Neurology ");
        var response = mvc.perform(post("/api/doctors").with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registrationJson(request, request.account())
                                .replace("\"email\":", "\"role\":\"ADMIN\",\"email\":")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("New Doctor"))
                .andExpect(jsonPath("$.account.role").value("DOCTOR"))
                .andExpect(jsonPath("$.account.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andReturn().getResponse();
        JsonNode body = mapper.readTree(response.getContentAsString());
        User account = users.findById(body.get("account").get("id").asLong()).orElseThrow();
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, account.getPasswordHash())).isTrue();

        mvc.perform(get("/api/doctors/me").with(httpBasic(email, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(body.get("id").asLong()));
    }

    @Test
    void administratorCreatesPatientAndNewAccountCanAuthenticate() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var request = new CreatePatientRequest(new UserRegistrationRequest(email, PASSWORD), "New Patient", "+970123456789");
        mvc.perform(post("/api/patients").with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registrationJson(request, request.account())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.account.role").value("PATIENT"))
                .andExpect(content().string(not(containsString(PASSWORD))));

        mvc.perform(get("/api/patients/me").with(httpBasic(email, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(request.phone()));
    }

    @Test
    void invalidRegistrationDoesNotCreateAnAccount() throws Exception {
        long count = users.count();
        String body = "{\"account\":{\"email\":\"invalid\",\"password\":\"short\"},\"name\":\" \",\"specialty\":\"\"}";
        mvc.perform(post("/api/doctors").with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['account.email']").exists())
                .andExpect(jsonPath("$.fieldErrors['account.password']").exists())
                .andExpect(content().string(not(containsString("short"))));
        assertThat(users.count()).isEqualTo(count);
    }

    @Test
    void duplicateEmailConflictsAcrossRolesWithoutCreatingAnotherAccount() throws Exception {
        long count = users.count();
        var request = new CreateDoctorRequest(new UserRegistrationRequest(
                patient.getUser().getEmail().toUpperCase(Locale.ROOT), PASSWORD), "Duplicate", "Neurology");
        mvc.perform(post("/api/doctors").with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registrationJson(request, request.account())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
        assertThat(users.count()).isEqualTo(count);
    }

    @Test
    void patientCanSearchSpecialtyWithoutReceivingAccountData() throws Exception {
        mvc.perform(get("/api/doctors").param("specialty", doctor.getSpecialty().toUpperCase(Locale.ROOT))
                        .with(httpBasic(patient.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(doctor.getId()))
                .andExpect(jsonPath("$[0].account").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void searchReturnsEmptyListForUnknownSpecialty() throws Exception {
        mvc.perform(get("/api/doctors").param("specialty", UUID.randomUUID().toString())
                        .with(httpBasic(patient.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void searchRejectsBlankAndOverlongSpecialties() throws Exception {
        for (String specialty : List.of(" ", "x".repeat(101))) {
            mvc.perform(get("/api/doctors").param("specialty", specialty)
                            .with(httpBasic(admin.getEmail(), PASSWORD)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void administratorReadsDoctorDetailsAndPatientList() throws Exception {
        mvc.perform(get("/api/doctors/{id}", doctor.getId()).with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.account.email").value(doctor.getUser().getEmail()));
        mvc.perform(get("/api/patients").with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(patient.getUser().getEmail())));
    }

    @Test
    void patientCannotReadAdministrativeDoctorDetails() throws Exception {
        mvc.perform(get("/api/doctors/{id}", doctor.getId())
                        .with(httpBasic(patient.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorUpdatesDoctorAndChangesArePersisted() throws Exception {
        Long id = doctor.getId();
        mvc.perform(put("/api/doctors/{id}", id).with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new UpdateDoctorRequest("Updated Doctor", "Oncology"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.specialty").value("Oncology"));
        entityManager.flush();
        entityManager.clear();
        assertThat(doctors.findById(id).orElseThrow().getName()).isEqualTo("Updated Doctor");
    }

    @Test
    void administratorUpdatesPatientAndCanClearPhone() throws Exception {
        mvc.perform(put("/api/patients/{id}", patient.getId()).with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new UpdatePatientRequest("Updated Patient", null))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Patient"))
                .andExpect(jsonPath("$.phone").isEmpty());
    }

    @Test
    void patientUpdatesOnlyOwnDetailsWithoutChangingRoleOrEmail() throws Exception {
        String email = patient.getUser().getEmail();
        String body = "{\"name\":\"My Updated Name\",\"phone\":\"12345\",\"account\":{\"role\":\"ADMIN\",\"email\":\"changed@example.com\"}}";
        mvc.perform(put("/api/patients/me").with(httpBasic(email, PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(patient.getId()))
                .andExpect(jsonPath("$.account.email").value(email))
                .andExpect(jsonPath("$.account.role").value("PATIENT"));
        assertThat(otherPatient.getName()).isEqualTo("Other Patient");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"DOCTOR", "PATIENT"})
    void clinicalRolesCannotManageOtherAccounts(Role role) throws Exception {
        String email = role == Role.DOCTOR ? doctor.getUser().getEmail() : patient.getUser().getEmail();
        for (String path : List.of("/api/doctors", "/api/patients")) {
            mvc.perform(post(path).with(httpBasic(email, PASSWORD)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        for (String path : List.of("/api/doctors/" + doctor.getId(), "/api/patients/" + otherPatient.getId())) {
            mvc.perform(put(path).with(httpBasic(email, PASSWORD)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete(path).with(httpBasic(email, PASSWORD)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/patients").with(httpBasic(email, PASSWORD))).andExpect(status().isForbidden());
    }

    @Test
    void missingCsrfTokenPreventsRegistration() throws Exception {
        mvc.perform(post("/api/patients").with(httpBasic(admin.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF token is missing or invalid"));
    }

    @Test
    void invalidProfileUpdateReturnsValidationErrors() throws Exception {
        mvc.perform(put("/api/patients/me").with(httpBasic(patient.getUser().getEmail(), PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void deletingUnusedDoctorRemovesUnbookedSlotsAndLogin() throws Exception {
        String email = doctor.getUser().getEmail();
        Long id = doctor.getId();
        slots.saveAndFlush(new DoctorSlot(doctor, Instant.parse("2030-01-07T10:00:00Z")));
        mvc.perform(delete("/api/doctors/{id}", id).with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf()))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(doctors.findById(id)).isEmpty();
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
        assertThat(slots.findByDoctorIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(id, Instant.EPOCH)).isEmpty();
        mvc.perform(get("/api/auth/me").with(httpBasic(email, PASSWORD))).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingUnusedPatientRemovesLogin() throws Exception {
        String email = patient.getUser().getEmail();
        Long id = patient.getId();
        mvc.perform(delete("/api/patients/{id}", id).with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf()))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(patients.findById(id)).isEmpty();
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(AppointmentStatus.class)
    void appointmentHistoryPreventsDoctorAndPatientDeletion(AppointmentStatus appointmentStatus) throws Exception {
        DoctorSlot slot = slots.saveAndFlush(new DoctorSlot(doctor, Instant.parse("2030-01-07T10:00:00Z")));
        Appointment appointment = appointments.saveAndFlush(new Appointment(slot, patient));
        entityManager.createNativeQuery("UPDATE appointments SET status=:status WHERE id=:id")
                .setParameter("status", appointmentStatus.name()).setParameter("id", appointment.getId()).executeUpdate();
        mvc.perform(delete("/api/doctors/{id}", doctor.getId()).with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/patients/{id}", patient.getId()).with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(users.existsById(doctor.getUser().getId())).isTrue();
        assertThat(users.existsById(patient.getUser().getId())).isTrue();
        assertThat(appointments.existsById(appointment.getId())).isTrue();
    }

    @Test
    void missingProfilesReturnNotFoundForUpdateAndDelete() throws Exception {
        mvc.perform(put("/api/doctors/{id}", Long.MAX_VALUE).with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Missing\",\"specialty\":\"None\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/patients/{id}", Long.MAX_VALUE).with(httpBasic(admin.getEmail(), PASSWORD)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceCreationIsAlsoRestrictedToAdministrators() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                patient.getUser().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
        try {
            assertThatThrownBy(() -> doctorService.create(new CreateDoctorRequest(
                    new UserRegistrationRequest("new@example.com", PASSWORD), "Doctor", "Cardiology")))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private User user(Role role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", HASH, role));
    }

    private String registrationJson(Object request, UserRegistrationRequest account) throws Exception {
        ObjectNode json = mapper.valueToTree(request);
        json.putObject("account").put("email", account.email()).put("password", account.password());
        return mapper.writeValueAsString(json);
    }

    private RequestPostProcessor csrf() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode token = mapper.readTree(response.getContentAsString());
        MockCookie cookie = MockCookie.parse(response.getHeader(HttpHeaders.SET_COOKIE));
        return request -> {
            request.setCookies(cookie);
            request.addHeader(token.get("headerName").asText(), token.get("token").asText());
            return request;
        };
    }
}
