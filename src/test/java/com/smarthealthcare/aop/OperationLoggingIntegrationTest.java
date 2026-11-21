package com.smarthealthcare.aop;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthcare.entity.Doctor;
import com.smarthealthcare.entity.DoctorSlot;
import com.smarthealthcare.entity.Patient;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.repository.AppointmentRepository;
import com.smarthealthcare.repository.DoctorRepository;
import com.smarthealthcare.repository.DoctorSlotRepository;
import com.smarthealthcare.repository.PatientRepository;
import com.smarthealthcare.repository.PrescriptionRepository;
import com.smarthealthcare.repository.UserRepository;
import com.smarthealthcare.service.AppointmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.AspectJAroundAdvice;
import org.springframework.aop.framework.Advised;
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
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.bootstrap.admin.enabled=false")
@AutoConfigureMockMvc
class OperationLoggingIntegrationTest {

    private static final String PASSWORD = "test-only-password";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private DoctorRepository doctors;
    @Autowired private PatientRepository patients;
    @Autowired private DoctorSlotRepository slots;
    @Autowired private AppointmentRepository appointments;
    @Autowired private PrescriptionRepository prescriptions;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AppointmentService service;

    private final Logger appointmentLogger = (Logger) LoggerFactory.getLogger("healthcare.appointments");
    private final Logger prescriptionLogger = (Logger) LoggerFactory.getLogger("healthcare.prescriptions");
    private final ListAppender<ILoggingEvent> appointmentEvents = new ListAppender<>();
    private final ListAppender<ILoggingEvent> prescriptionEvents = new ListAppender<>();
    private Doctor doctor;
    private Patient patient;
    private DoctorSlot slot;

    @BeforeEach
    void commitFixturesAndCaptureEvents() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            doctor = doctors.saveAndFlush(new Doctor(user(Role.DOCTOR), "Logging Doctor", "Test"));
            patient = patients.saveAndFlush(new Patient(user(Role.PATIENT), "Logging Patient", null));
            slot = slots.saveAndFlush(new DoctorSlot(doctor,
                    Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)));
        });
        appointmentEvents.start();
        prescriptionEvents.start();
        appointmentLogger.addAppender(appointmentEvents);
        prescriptionLogger.addAppender(prescriptionEvents);
    }

    @AfterEach
    void removeOnlyOwnedFixturesAndDetachAppenders() {
        appointmentLogger.detachAppender(appointmentEvents);
        prescriptionLogger.detachAppender(prescriptionEvents);
        appointmentEvents.stop();
        prescriptionEvents.stop();
        prescriptions.deleteAll(prescriptions.findByPatientIdOrderByCreatedAtDescIdDesc(patient.getId()));
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            appointments.deleteAll(appointments.findBySlotDoctorIdOrderBySlotStartTimeAsc(doctor.getId()));
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

    @Test
    void bookingLogsCommittedSuccessAndRejectedDoubleBookingWithRequestScopedIds() throws Exception {
        book();
        mvc.perform(post("/api/appointments").with(auth(patient.getUser())).with(csrf(patient.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + slot.getId() + "}"))
                .andExpect(status().isConflict());

        var messages = messages(appointmentEvents);
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).contains("operation=book outcome=ATTEMPT");
        assertThat(messages.get(1)).contains("operation=book outcome=SUCCEEDED");
        assertThat(messages.get(3)).contains("operation=book outcome=CONFLICT",
                "Double booking attempt prevented for Logging Doctor at 12:00 AM UTC",
                slot.getStartTime().toString().substring(0, 10));
        String firstId = requestId(messages.get(0));
        assertThat(UUID.fromString(firstId)).isNotNull();
        assertThat(requestId(messages.get(1))).isEqualTo(firstId);
        assertThat(requestId(messages.get(2))).isNotEqualTo(firstId);
        assertThat(messages).allSatisfy(message -> assertThat(message).doesNotContain(PASSWORD, HASH, patient.getUser().getEmail()));
    }

    @Test
    void cancellationLogsTheStatusTransitionAndRepeatedCancellationConflict() throws Exception {
        Long id = book().get("id").asLong();
        appointmentEvents.list.clear();
        mvc.perform(patch("/api/appointments/{id}/cancel", id).with(auth(patient.getUser())).with(csrf(patient.getUser())))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/appointments/{id}/cancel", id).with(auth(patient.getUser())).with(csrf(patient.getUser())))
                .andExpect(status().isConflict());
        assertThat(messages(appointmentEvents)).hasSize(4)
                .anyMatch(message -> message.contains("operation=cancel outcome=SUCCEEDED"))
                .anyMatch(message -> message.contains("operation=cancel outcome=CONFLICT"));
    }

    @Test
    void prescriptionWritesLogOutcomesWithoutClinicalContent() throws Exception {
        Long appointmentId = book().get("id").asLong();
        mvc.perform(patch("/api/appointments/{id}/complete", appointmentId).with(auth(doctor.getUser())).with(csrf(doctor.getUser())))
                .andExpect(status().isOk());
        var body = mapper.createObjectNode().put("appointmentId", appointmentId).put("notes", "PRIVATE-NOTES-marker");
        body.putArray("medicines").addObject().put("name", "PRIVATE-MEDICINE-marker").put("dosage", "PRIVATE-DOSAGE-marker");
        var response = mvc.perform(post("/api/prescriptions").with(auth(doctor.getUser())).with(csrf(doctor.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse();
        JsonNode prescription = mapper.readTree(response.getContentAsString());
        body.remove("appointmentId");
        body.put("version", prescription.get("version").asLong());
        String path = "/api/prescriptions/" + prescription.get("id").asText();
        mvc.perform(put(path).with(auth(doctor.getUser())).with(csrf(doctor.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk());
        mvc.perform(put(path).with(auth(doctor.getUser())).with(csrf(doctor.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isConflict());

        assertThat(messages(prescriptionEvents)).hasSize(6)
                .anyMatch(message -> message.contains("operation=create outcome=SUCCEEDED"))
                .anyMatch(message -> message.contains("operation=update outcome=SUCCEEDED"))
                .anyMatch(message -> message.contains("operation=update outcome=CONFLICT"))
                .allSatisfy(message -> assertThat(message).doesNotContain("PRIVATE-", PASSWORD, HASH, patient.getUser().getEmail()));
        assertThat(prescriptionEvents.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    @Test
    void loggingAdviceWrapsTheTransactionInterceptor() {
        var advice = Arrays.stream(((Advised) service).getAdvisors()).map(advisor -> advisor.getAdvice()).toList();
        int logging = -1;
        int transaction = -1;
        for (int index = 0; index < advice.size(); index++) {
            if (advice.get(index) instanceof AspectJAroundAdvice) logging = index;
            if (advice.get(index) instanceof TransactionInterceptor) transaction = index;
        }
        assertThat(logging).isNotNegative();
        assertThat(transaction).isGreaterThan(logging);
    }

    private JsonNode book() throws Exception {
        var response = mvc.perform(post("/api/appointments").with(auth(patient.getUser())).with(csrf(patient.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + slot.getId() + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return mapper.readTree(response.getContentAsString());
    }

    private List<String> messages(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private String requestId(String message) {
        return message.substring("requestId=".length(), message.indexOf(' '));
    }

    private User user(Role role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", HASH, role));
    }

    private RequestPostProcessor auth(User user) {
        return httpBasic(user.getEmail(), PASSWORD);
    }

    private RequestPostProcessor csrf(User user) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").with(auth(user)))
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
