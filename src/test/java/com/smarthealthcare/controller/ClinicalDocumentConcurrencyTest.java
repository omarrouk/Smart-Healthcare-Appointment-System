package com.smarthealthcare.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smarthealthcare.document.MedicalRecord;
import com.smarthealthcare.document.Medicine;
import com.smarthealthcare.document.Prescription;
import com.smarthealthcare.entity.Appointment;
import com.smarthealthcare.entity.Doctor;
import com.smarthealthcare.entity.DoctorSlot;
import com.smarthealthcare.entity.Patient;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.repository.AppointmentRepository;
import com.smarthealthcare.repository.DoctorRepository;
import com.smarthealthcare.repository.DoctorSlotRepository;
import com.smarthealthcare.repository.MedicalRecordRepository;
import com.smarthealthcare.repository.PatientRepository;
import com.smarthealthcare.repository.PrescriptionRepository;
import com.smarthealthcare.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.bootstrap.admin.enabled=false")
@AutoConfigureMockMvc
class ClinicalDocumentConcurrencyTest {

    private static final String PASSWORD = "test-only-password";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    private static final List<Medicine> MEDICINES = List.of(new Medicine("Test medicine", "Test dosage", null));

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private DoctorRepository doctors;
    @Autowired private PatientRepository patients;
    @Autowired private DoctorSlotRepository slots;
    @Autowired private AppointmentRepository appointments;
    @Autowired private PrescriptionRepository prescriptions;
    @Autowired private MedicalRecordRepository records;
    @Autowired private PlatformTransactionManager transactionManager;

    private Doctor doctor;
    private Patient patient;
    private DoctorSlot slot;
    private Appointment appointment;

    @BeforeEach
    void commitRelationalFixturesForIndependentRequests() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            doctor = doctors.saveAndFlush(new Doctor(user(Role.DOCTOR), "Concurrent Clinical Doctor", "Test"));
            patient = patients.saveAndFlush(new Patient(user(Role.PATIENT), "Concurrent Clinical Patient", null));
            slot = slots.saveAndFlush(new DoctorSlot(doctor,
                    Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)));
            appointment = new Appointment(slot, patient);
            appointment.complete();
            appointments.saveAndFlush(appointment);
        });
    }

    @AfterEach
    void removeOnlyOwnedFixturesFromBothDatabases() {
        prescriptions.deleteAll(prescriptions.findByPatientIdOrderByCreatedAtDescIdDesc(patient.getId()));
        records.deleteAll(records.findByPatientIdOrderByCreatedAtDescIdDesc(patient.getId()));
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            appointments.deleteById(appointment.getId());
            appointments.flush();
            slots.deleteById(slot.getId());
            slots.flush();
            doctors.deleteById(doctor.getId());
            patients.deleteById(patient.getId());
            doctors.flush();
            patients.flush();
            users.deleteAllById(List.of(doctor.getUser().getId(), patient.getUser().getId()));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"prescriptions", "medical-records"})
    void competingHttpUpdatesCannotOverwriteTheWinningVersion(String resource) throws Exception {
        String id = resource.equals("prescriptions")
                ? prescriptions.save(new Prescription(doctor.getId(), patient.getId(), appointment.getId(),
                        MEDICINES, "Original notes")).getId()
                : records.save(new MedicalRecord(doctor.getId(), patient.getId(), appointment.getId(),
                        MEDICINES, "Original notes", List.of())).getId();
        String path = "/api/" + resource + "/" + id;
        var firstToken = csrf();
        var secondToken = csrf();
        ObjectNode firstBody = updateBody(resource, "First concurrent update");
        ObjectNode secondBody = updateBody(resource, "Second concurrent update");
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        int firstStatus;
        int secondStatus;
        try {
            var first = executor.submit(() -> update(path, firstBody, firstToken, ready, start));
            var second = executor.submit(() -> update(path, secondBody, secondToken, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstStatus = first.get(30, TimeUnit.SECONDS);
            secondStatus = second.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(200, 409);
        String winningNotes = firstStatus == 200 ? firstBody.get("notes").asText() : secondBody.get("notes").asText();
        mvc.perform(get(path).with(httpBasic(patient.getUser().getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes").value(winningNotes))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.patientId").value(patient.getId()))
                .andExpect(jsonPath("$.doctorId").value(doctor.getId()));
    }

    private int update(String path, ObjectNode body, RequestPostProcessor token,
                       CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent update did not start");
        return mvc.perform(put(path).with(httpBasic(doctor.getUser().getEmail(), PASSWORD)).with(token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andReturn().getResponse().getStatus();
    }

    private ObjectNode updateBody(String resource, String notes) {
        ObjectNode body = mapper.createObjectNode().put("version", 0).put("notes", notes);
        body.set("medicines", mapper.valueToTree(MEDICINES));
        if (resource.equals("medical-records")) body.putArray("labResults");
        return body;
    }

    private User user(Role role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", HASH, role));
    }

    private RequestPostProcessor csrf() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").with(httpBasic(doctor.getUser().getEmail(), PASSWORD)))
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
