package com.smarthealthcare.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthcare.dto.BookAppointmentRequest;
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
import com.smarthealthcare.service.AppointmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.bootstrap.admin.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AppointmentIntegrationTest {

    private static final String PASSWORD = "test-only-password";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private DoctorRepository doctors;
    @Autowired private PatientRepository patients;
    @Autowired private DoctorSlotRepository slots;
    @Autowired private AppointmentRepository appointments;
    @Autowired private AppointmentService service;
    @Autowired private EntityManager entityManager;

    private User admin;
    private Doctor doctor;
    private Doctor otherDoctor;
    private Patient patient;
    private Patient otherPatient;
    private DoctorSlot slot;

    @BeforeEach
    void createFixtures() {
        admin = user(Role.ADMIN);
        doctor = doctors.saveAndFlush(new Doctor(user(Role.DOCTOR), "Doctor", "Cardiology"));
        otherDoctor = doctors.saveAndFlush(new Doctor(user(Role.DOCTOR), "Other Doctor", "Neurology"));
        patient = patients.saveAndFlush(new Patient(user(Role.PATIENT), "Patient", null));
        otherPatient = patients.saveAndFlush(new Patient(user(Role.PATIENT), "Other Patient", null));
        slot = slots.saveAndFlush(new DoctorSlot(doctor, futureStart()));
    }

    @Test
    void adminCreatesAlignedSlotWithThirtyMinuteDuration() throws Exception {
        Instant start = futureStart().plusSeconds(1800);
        mvc.perform(post("/api/doctors/{id}/slots", doctor.getId()).with(auth(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"" + start + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.doctorId").value(doctor.getId()))
                .andExpect(jsonPath("$.startTime").value(start.toString()))
                .andExpect(jsonPath("$.endTime").value(start.plusSeconds(1800).toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"startTime\":\"2020-01-01T10:00:00Z\"}",
            "{\"startTime\":\"2099-01-01T10:15:00Z\"}", "{\"startTime\":\"2099-01-01T10:00:00.001Z\"}",
            "{\"startTime\":\"not-a-time\"}"})
    void rejectsInvalidSlotTimes(String body) throws Exception {
        mvc.perform(post("/api/doctors/{id}/slots", doctor.getId()).with(auth(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateSlotReturnsConflict() throws Exception {
        mvc.perform(post("/api/doctors/{id}/slots", doctor.getId()).with(auth(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"" + slot.getStartTime() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void availabilityExcludesPastBookedCompletedAndDisabledSlots() throws Exception {
        var booked = slots.saveAndFlush(new DoctorSlot(doctor, futureStart().plusSeconds(1800)));
        var completed = slots.saveAndFlush(new DoctorSlot(doctor, futureStart().plusSeconds(3600)));
        slots.saveAndFlush(new DoctorSlot(doctor, Instant.parse("2020-01-01T10:00:00Z")));
        appointments.saveAndFlush(new Appointment(booked, patient));
        var done = new Appointment(completed, patient);
        done.complete();
        appointments.saveAndFlush(done);
        var cancelled = new Appointment(slot, patient);
        cancelled.cancel();
        appointments.saveAndFlush(cancelled);

        mvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).with(auth(patient.getUser())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(slot.getId()));
        doctor.getUser().setEnabled(false);
        entityManager.flush();
        mvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).with(auth(patient.getUser())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void slotManagementIsAdminOnlyAndDoctorsCanReadOnlyTheirOwnAvailability() throws Exception {
        mvc.perform(post("/api/doctors/{id}/slots", doctor.getId()).with(auth(patient.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/doctors/{id}/slots/{slot}", doctor.getId(), slot.getId())
                        .with(auth(doctor.getUser())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).with(auth(otherDoctor.getUser())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).with(auth(doctor.getUser())))
                .andExpect(status().isOk());
    }

    @Test
    void adminDeletesUnusedSlotAndMismatchedDoctorReturnsNotFound() throws Exception {
        mvc.perform(delete("/api/doctors/{id}/slots/{slot}", otherDoctor.getId(), slot.getId())
                        .with(auth(admin)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/doctors/{id}/slots/{slot}", doctor.getId(), slot.getId())
                        .with(auth(admin)).with(csrf())).andExpect(status().isNoContent());
        assertThat(slots.existsById(slot.getId())).isFalse();
    }

    @ParameterizedTest
    @EnumSource(AppointmentStatus.class)
    void slotWithAnyAppointmentHistoryCannotBeDeleted(AppointmentStatus state) throws Exception {
        Appointment appointment = new Appointment(slot, patient);
        if (state == AppointmentStatus.CANCELLED) appointment.cancel();
        if (state == AppointmentStatus.COMPLETED) appointment.complete();
        appointments.saveAndFlush(appointment);
        mvc.perform(delete("/api/doctors/{id}/slots/{slot}", doctor.getId(), slot.getId())
                        .with(auth(admin)).with(csrf())).andExpect(status().isConflict());
    }

    @Test
    void bookingUsesAuthenticatedPatientAndRejectsDoubleBooking() throws Exception {
        var response = mvc.perform(post("/api/appointments").with(auth(patient.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + slot.getId() + ",\"patientId\":" + otherPatient.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.patientId").value(patient.getId()))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(content().string(not(containsString("password")))).andReturn().getResponse();
        mvc.perform(get(response.getHeader("Location")).with(auth(patient.getUser()))).andExpect(status().isOk());
        mvc.perform(post("/api/appointments").with(auth(otherPatient.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Slot is already booked"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"slotId\":0}", "{\"slotId\":-1}"})
    void rejectsInvalidBookingBodies(String body) throws Exception {
        mvc.perform(post("/api/appointments").with(auth(patient.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.slotId").exists());
    }

    @Test
    void missingResourcesReturnNotFound() throws Exception {
        mvc.perform(post("/api/appointments").with(auth(patient.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + Long.MAX_VALUE + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/doctors/{id}/slots", Long.MAX_VALUE).with(auth(patient.getUser())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/appointments/{id}", Long.MAX_VALUE).with(auth(patient.getUser())))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/appointments/{id}/cancel", Long.MAX_VALUE).with(auth(patient.getUser())).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void pastSlotCannotBeBooked() throws Exception {
        var past = slots.saveAndFlush(new DoctorSlot(doctor, Instant.parse("2020-01-01T10:00:00Z")));
        mvc.perform(post("/api/appointments").with(auth(patient.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + past.getId() + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void patientCancelsOwnBookingAndAnotherPatientCanRebook() throws Exception {
        Appointment appointment = appointments.saveAndFlush(new Appointment(slot, patient));
        mvc.perform(patch("/api/appointments/{id}/cancel", appointment.getId()).with(auth(patient.getUser())).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/appointments").with(auth(otherPatient.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody())).andExpect(status().isCreated());
        assertThat(appointments.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void onlyOwningPatientCanReadOrCancelAndDoctorCannotCancel() throws Exception {
        Appointment appointment = appointments.saveAndFlush(new Appointment(slot, patient));
        mvc.perform(get("/api/appointments/{id}", appointment.getId()).with(auth(otherPatient.getUser())))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/appointments/{id}/cancel", appointment.getId()).with(auth(otherPatient.getUser())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/appointments/{id}/cancel", appointment.getId()).with(auth(doctor.getUser())).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void onlyOwningDoctorCanReadOrCompleteAndPatientsCannotComplete() throws Exception {
        Appointment appointment = appointments.saveAndFlush(new Appointment(slot, patient));
        mvc.perform(get("/api/appointments/{id}", appointment.getId()).with(auth(otherDoctor.getUser())))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/appointments/{id}/complete", appointment.getId()).with(auth(otherDoctor.getUser())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/appointments/{id}/complete", appointment.getId()).with(auth(patient.getUser())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/appointments/{id}/complete", appointment.getId()).with(auth(doctor.getUser())).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(patch("/api/appointments/{id}/cancel", appointment.getId()).with(auth(patient.getUser())).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/appointments").with(auth(otherPatient.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody())).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"CANCELLED", "COMPLETED"})
    void terminalAppointmentsRejectFurtherTransitions(AppointmentStatus state) throws Exception {
        Appointment appointment = new Appointment(slot, patient);
        if (state == AppointmentStatus.CANCELLED) appointment.cancel();
        else appointment.complete();
        appointments.saveAndFlush(appointment);
        mvc.perform(patch("/api/appointments/{id}/cancel", appointment.getId()).with(auth(patient.getUser())).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/appointments/{id}/complete", appointment.getId()).with(auth(doctor.getUser())).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void personalListsDoNotExposeOtherPatientsOrDoctorsAppointments() throws Exception {
        Appointment own = appointments.saveAndFlush(new Appointment(slot, patient));
        var otherSlot = slots.saveAndFlush(new DoctorSlot(otherDoctor, futureStart()));
        appointments.saveAndFlush(new Appointment(otherSlot, otherPatient));
        for (User user : List.of(patient.getUser(), doctor.getUser())) {
            mvc.perform(get("/api/appointments/me").with(auth(user))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(own.getId()));
        }
        mvc.perform(get("/api/appointments/me").with(auth(admin))).andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments").with(auth(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody())).andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments").with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody())).andExpect(status().isForbidden());
    }

    @Test
    void appointmentWritesRequireCsrfAndReadsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/appointments/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/appointments").with(auth(patient.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("CSRF token is missing or invalid"));
    }

    @Test
    void serviceAlsoRestrictsBookingToPatients() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                admin.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            assertThatThrownBy(() -> service.book(new BookAppointmentRequest(slot.getId())))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private User user(Role role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", HASH, role));
    }

    private Instant futureStart() {
        return Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
    }

    private String bookingBody() {
        return "{\"slotId\":" + slot.getId() + "}";
    }

    private RequestPostProcessor auth(User user) {
        return httpBasic(user.getEmail(), PASSWORD);
    }

    private RequestPostProcessor csrf() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").with(auth(admin)))
                .andExpect(status().isOk()).andReturn().getResponse();
        var token = mapper.readTree(response.getContentAsString());
        MockCookie cookie = MockCookie.parse(response.getHeader(HttpHeaders.SET_COOKIE));
        return request -> {
            request.setCookies(cookie);
            request.addHeader(token.get("headerName").asText(), token.get("token").asText());
            return request;
        };
    }
}
