package com.smarthealthcare.controller;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.bootstrap.admin.enabled=false")
@AutoConfigureMockMvc
class AppointmentConcurrencyTest {

    private static final String PASSWORD = "test-only-password";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private DoctorRepository doctors;
    @Autowired private PatientRepository patients;
    @Autowired private DoctorSlotRepository slots;
    @Autowired private AppointmentRepository appointments;
    @Autowired private PlatformTransactionManager transactionManager;

    private Doctor doctor;
    private Patient patient;
    private Patient otherPatient;
    private DoctorSlot slot;

    @BeforeEach
    void commitFixturesForIndependentRequestTransactions() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            doctor = doctors.saveAndFlush(new Doctor(user(Role.DOCTOR), "Concurrent Doctor", "Test"));
            patient = patients.saveAndFlush(new Patient(user(Role.PATIENT), "Concurrent Patient", null));
            otherPatient = patients.saveAndFlush(new Patient(user(Role.PATIENT), "Other Concurrent Patient", null));
            slot = slots.saveAndFlush(new DoctorSlot(doctor,
                    Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)));
        });
    }

    @AfterEach
    void deleteOnlyThisTestsCommittedFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            var testAppointments = appointments.findBySlotDoctorIdOrderBySlotStartTimeAsc(doctor.getId());
            appointments.deleteAll(testAppointments);
            appointments.flush();
            slots.deleteById(slot.getId());
            slots.flush();
            doctors.deleteById(doctor.getId());
            patients.deleteAllById(List.of(patient.getId(), otherPatient.getId()));
            doctors.flush();
            patients.flush();
            users.deleteAllById(List.of(doctor.getUser().getId(), patient.getUser().getId(), otherPatient.getUser().getId()));
        });
    }

    @Test
    void twoPatientsCannotBookTheSameSlotConcurrently() throws Exception {
        var firstToken = csrf(patient.getUser());
        var secondToken = csrf(otherPatient.getUser());

        List<Integer> responses = race(
                () -> book(patient.getUser(), firstToken),
                () -> book(otherPatient.getUser(), secondToken));

        assertThat(responses).containsExactlyInAnyOrder(201, 409);
        assertThat(appointments.findBySlotDoctorIdOrderBySlotStartTimeAsc(doctor.getId()))
                .singleElement().extracting(Appointment::getStatus).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void cancellationAndCompletionCannotOverwriteEachOther() throws Exception {
        Long appointmentId = new TransactionTemplate(transactionManager).execute(transaction ->
                appointments.saveAndFlush(new Appointment(slot, patient)).getId());
        var patientToken = csrf(patient.getUser());
        var doctorToken = csrf(doctor.getUser());

        List<Integer> responses = race(
                () -> mvc.perform(patch("/api/appointments/{id}/cancel", appointmentId)
                                .with(httpBasic(patient.getUser().getEmail(), PASSWORD)).with(patientToken))
                        .andReturn().getResponse().getStatus(),
                () -> mvc.perform(patch("/api/appointments/{id}/complete", appointmentId)
                                .with(httpBasic(doctor.getUser().getEmail(), PASSWORD)).with(doctorToken))
                        .andReturn().getResponse().getStatus());

        assertThat(responses).containsExactlyInAnyOrder(200, 409);
        assertThat(appointments.findById(appointmentId).orElseThrow().getStatus())
                .isIn(AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED);
    }

    private int book(User user, RequestPostProcessor token) throws Exception {
        return mvc.perform(post("/api/appointments").with(httpBasic(user.getEmail(), PASSWORD)).with(token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + slot.getId() + "}"))
                .andReturn().getResponse().getStatus();
    }

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var firstResult = executor.submit(() -> runWhenReady(first, ready, start));
            var secondResult = executor.submit(() -> runWhenReady(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int runWhenReady(Callable<Integer> request, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent request did not start");
        }
        return request.call();
    }

    private User user(Role role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", HASH, role));
    }

    private RequestPostProcessor csrf(User user) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").with(httpBasic(user.getEmail(), PASSWORD)))
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
