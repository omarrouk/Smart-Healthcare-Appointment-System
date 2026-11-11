package com.smarthealthcare.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smarthealthcare.document.MedicalRecord;
import com.smarthealthcare.document.Prescription;
import com.smarthealthcare.dto.CreateMedicalRecordRequest;
import com.smarthealthcare.dto.CreatePrescriptionRequest;
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
import com.smarthealthcare.repository.MedicalRecordRepository;
import com.smarthealthcare.repository.PatientRepository;
import com.smarthealthcare.repository.PrescriptionRepository;
import com.smarthealthcare.repository.UserRepository;
import com.smarthealthcare.service.MedicalRecordService;
import com.smarthealthcare.service.PrescriptionService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.bootstrap.admin.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ClinicalDocumentsIntegrationTest {

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
    @Autowired private MedicalRecordRepository records;
    @Autowired private PrescriptionService prescriptionService;
    @Autowired private MedicalRecordService recordService;
    @Autowired private MongoTemplate mongo;

    private User admin;
    private Doctor doctor;
    private Doctor otherDoctor;
    private Patient patient;
    private Patient otherPatient;
    private Appointment completed;
    private int slotOffset;

    @BeforeEach
    void createFixtures() {
        admin = user(Role.ADMIN);
        doctor = doctors.saveAndFlush(new Doctor(user(Role.DOCTOR), "Clinical Doctor", "Test"));
        otherDoctor = doctors.saveAndFlush(new Doctor(user(Role.DOCTOR), "Other Clinical Doctor", "Test"));
        patient = patients.saveAndFlush(new Patient(user(Role.PATIENT), "Clinical Patient", null));
        otherPatient = patients.saveAndFlush(new Patient(user(Role.PATIENT), "Other Clinical Patient", null));
        completed = appointment(doctor, patient, AppointmentStatus.COMPLETED);
    }

    @AfterEach
    void removeOnlyFixtureDocumentsFromMongo() {
        for (Patient fixture : List.of(patient, otherPatient)) {
            prescriptions.deleteAll(prescriptions.findByPatientIdOrderByCreatedAtDescIdDesc(fixture.getId()));
            records.deleteAll(records.findByPatientIdOrderByCreatedAtDescIdDesc(fixture.getId()));
        }
    }

    @ParameterizedTest
    @EnumSource(ClinicalType.class)
    void doctorCreatesDocumentWithServerControlledReferencesAndAuditFields(ClinicalType type) throws Exception {
        ObjectNode body = createBody(type);
        body.put("doctorId", otherDoctor.getId());
        body.put("version", 999);
        body.put("createdAt", "2000-01-01T00:00:00Z");
        if (type == ClinicalType.PRESCRIPTION) body.put("patientId", otherPatient.getId());

        var response = mvc.perform(post(type.path).with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.doctorId").value(doctor.getId()))
                .andExpect(jsonPath("$.patientId").value(patient.getId()))
                .andExpect(jsonPath("$.appointmentId").value(completed.getId()))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty()).andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(content().string(not(containsString("password")))).andReturn().getResponse();

        JsonNode document = mapper.readTree(response.getContentAsString());
        assertThat(ObjectId.isValid(document.get("id").asText())).isTrue();
        assertThat(Instant.parse(document.get("createdAt").asText())).isAfter(Instant.parse("2000-01-01T00:00:00Z"));
        mvc.perform(get(response.getHeader("Location")).with(auth(patient.getUser())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes").value("Test clinical notes"))
                .andExpect(jsonPath("$.createdAt").value(document.get("createdAt").asText()));
    }

    @ParameterizedTest
    @EnumSource(ClinicalType.class)
    void authorUpdatesContentButCannotMoveTheDocument(ClinicalType type) throws Exception {
        JsonNode created = create(type);
        ObjectNode body = updateBody(type, created.get("version").asLong());
        body.put("doctorId", otherDoctor.getId());
        body.put("patientId", otherPatient.getId());
        body.put("appointmentId", Long.MAX_VALUE);
        body.put("createdAt", "2000-01-01T00:00:00Z");
        String url = type.path + "/" + created.get("id").asText();

        mvc.perform(put(url).with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes").value("Updated clinical notes"))
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.doctorId").value(doctor.getId()))
                .andExpect(jsonPath("$.patientId").value(patient.getId()))
                .andExpect(jsonPath("$.appointmentId").value(completed.getId()))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()));
        mvc.perform(get(url).with(auth(patient.getUser())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes").value("Updated clinical notes"));
    }

    @ParameterizedTest
    @EnumSource(ClinicalType.class)
    void staleClientVersionReturnsConflictAndPreservesTheSavedUpdate(ClinicalType type) throws Exception {
        JsonNode created = create(type);
        String url = type.path + "/" + created.get("id").asText();
        ObjectNode body = updateBody(type, created.get("version").asLong());
        mvc.perform(put(url).with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk());
        body.put("notes", "Stale content");
        mvc.perform(put(url).with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Document changed; reload it before updating"));
        mvc.perform(get(url).with(auth(patient.getUser()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Updated clinical notes"));
    }

    @ParameterizedTest
    @EnumSource(ClinicalType.class)
    void unrelatedUsersCannotReadOrUpdateDocumentsEvenAnotherTreatingDoctor(ClinicalType type) throws Exception {
        JsonNode created = create(type);
        appointment(otherDoctor, patient, AppointmentStatus.COMPLETED);
        String url = type.path + "/" + created.get("id").asText();
        ObjectNode body = updateBody(type, created.get("version").asLong());
        for (User outsider : List.of(otherDoctor.getUser(), otherPatient.getUser(), admin)) {
            mvc.perform(get(url).with(auth(outsider))).andExpect(status().isForbidden());
            mvc.perform(put(url).with(auth(outsider)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isForbidden());
        }
        mvc.perform(put(url).with(auth(patient.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(ClinicalType.class)
    void historyListsAreScopedToThePatientOrAuthorAndOrderedNewestFirst(ClinicalType type) throws Exception {
        JsonNode first = create(type);
        JsonNode second = create(type);
        for (User owner : List.of(patient.getUser(), doctor.getUser())) {
            mvc.perform(get(type.path + "/me").with(auth(owner))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(second.get("id").asText()))
                    .andExpect(jsonPath("$[1].id").value(first.get("id").asText()));
        }
        for (User outsider : List.of(otherPatient.getUser(), otherDoctor.getUser())) {
            mvc.perform(get(type.path + "/me").with(auth(outsider))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
        mvc.perform(get(type.path + "/me").with(auth(admin))).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(ClinicalType.class)
    void onlyTheAppointmentDoctorCanCreateClinicalDocuments(ClinicalType type) throws Exception {
        String body = createBody(type).toString();
        for (User outsider : List.of(otherDoctor.getUser(), patient.getUser(), admin)) {
            mvc.perform(post(type.path).with(auth(outsider)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        }
        assertThat(prescriptions.findByPatientIdOrderByCreatedAtDescIdDesc(patient.getId())).isEmpty();
        assertThat(records.findByPatientIdOrderByCreatedAtDescIdDesc(patient.getId())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"BOOKED", "CANCELLED"})
    void unfinishedAppointmentsCannotAuthorizeClinicalWrites(AppointmentStatus state) throws Exception {
        Appointment unfinished = appointment(doctor, patient, state);
        for (ClinicalType type : ClinicalType.values()) {
            ObjectNode body = createBody(type).put("appointmentId", unfinished.getId());
            mvc.perform(post(type.path).with(auth(doctor.getUser())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void recordWithoutAppointmentRequiresAnExistingCompletedDoctorPatientRelationship() throws Exception {
        ObjectNode body = createBody(ClinicalType.MEDICAL_RECORD);
        body.remove("appointmentId");
        mvc.perform(post("/api/medical-records").with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.appointmentId").isEmpty());
        body.put("patientId", otherPatient.getId());
        appointment(doctor, otherPatient, AppointmentStatus.BOOKED);
        mvc.perform(post("/api/medical-records").with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isForbidden());
        body.put("patientId", Long.MAX_VALUE);
        mvc.perform(post("/api/medical-records").with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isNotFound());
    }

    @Test
    void recordPatientMustMatchItsAppointment() throws Exception {
        ObjectNode body = createBody(ClinicalType.MEDICAL_RECORD).put("patientId", otherPatient.getId());
        mvc.perform(post("/api/medical-records").with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Patient does not match the appointment"));
    }

    @ParameterizedTest
    @EnumSource(ClinicalType.class)
    void invalidClinicalContentAndVersionsAreRejected(ClinicalType type) throws Exception {
        ObjectNode missingMedicines = createBody(type);
        missingMedicines.remove("medicines");
        ObjectNode invalidMedicine = createBody(type);
        ((ObjectNode) invalidMedicine.withArray("medicines").get(0)).put("name", " ");
        ObjectNode nullMedicine = createBody(type);
        nullMedicine.putArray("medicines").addNull();
        ObjectNode oversizedNotes = createBody(type).put("notes", "x".repeat(4001));
        ObjectNode negativeAppointment = createBody(type).put("appointmentId", -1);
        for (ObjectNode invalid : List.of(mapper.createObjectNode(), missingMedicines, invalidMedicine,
                nullMedicine, oversizedNotes, negativeAppointment)) {
            mvc.perform(post(type.path).with(auth(doctor.getUser())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(invalid.toString()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        }
        JsonNode created = create(type);
        ObjectNode invalidVersion = updateBody(type, -1);
        for (String body : List.of(invalidVersion.toString(), "{}")) {
            mvc.perform(put(type.path + "/" + created.get("id").asText()).with(auth(doctor.getUser())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void prescriptionNeedsAtLeastOneMedicineAndRecordNeedsNonblankNotes() throws Exception {
        ObjectNode prescription = createBody(ClinicalType.PRESCRIPTION);
        prescription.putArray("medicines");
        mvc.perform(post("/api/prescriptions").with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(prescription.toString())).andExpect(status().isBadRequest());
        ObjectNode record = createBody(ClinicalType.MEDICAL_RECORD).put("notes", " ");
        mvc.perform(post("/api/medical-records").with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(record.toString())).andExpect(status().isBadRequest());
        record.put("notes", "Valid test notes").putArray("labResults").add(" ");
        mvc.perform(post("/api/medical-records").with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(record.toString())).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(ClinicalType.class)
    void missingDocumentsAndAppointmentsReturnNotFound(ClinicalType type) throws Exception {
        String url = type.path + "/" + new ObjectId().toHexString();
        mvc.perform(get(url).with(auth(patient.getUser()))).andExpect(status().isNotFound());
        mvc.perform(put(url).with(auth(doctor.getUser())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(type, 0).toString())).andExpect(status().isNotFound());
        mvc.perform(post(type.path).with(auth(doctor.getUser())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(type).put("appointmentId", Long.MAX_VALUE).toString())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(ClinicalType.class)
    void authenticationCsrfAndHistoryDeletionRulesRemainEnforced(ClinicalType type) throws Exception {
        mvc.perform(get(type.path + "/me")).andExpect(status().isUnauthorized());
        mvc.perform(post(type.path).with(auth(doctor.getUser())).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(type).toString())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF token is missing or invalid"));
        JsonNode created = create(type);
        mvc.perform(delete(type.path + "/" + created.get("id").asText()).with(auth(doctor.getUser())).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void mongoVersionChecksRejectCompetingCopiesForBothDocumentTypes() throws Exception {
        String prescriptionId = create(ClinicalType.PRESCRIPTION).get("id").asText();
        Prescription first = prescriptions.findById(prescriptionId).orElseThrow();
        Prescription stale = prescriptions.findById(prescriptionId).orElseThrow();
        first.updateDetails(first.getMedicines(), "First saved update");
        prescriptions.save(first);
        stale.updateDetails(stale.getMedicines(), "Stale update");
        assertThatThrownBy(() -> prescriptions.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(prescriptions.findById(prescriptionId).orElseThrow().getNotes()).isEqualTo("First saved update");

        String recordId = create(ClinicalType.MEDICAL_RECORD).get("id").asText();
        MedicalRecord record = records.findById(recordId).orElseThrow();
        MedicalRecord staleRecord = records.findById(recordId).orElseThrow();
        record.updateDetails(record.getMedicines(), "First saved update", record.getLabResults());
        records.save(record);
        assertThatThrownBy(() -> records.save(staleRecord)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void historyIndexesExistAndClinicalContentIsRedactedFromDiagnosticStrings() throws Exception {
        assertThat(mongo.indexOps(Prescription.class).getIndexInfo()).extracting(IndexInfo::getName)
                .contains("idx_prescriptions_patient_history", "idx_prescriptions_doctor_history");
        assertThat(mongo.indexOps(MedicalRecord.class).getIndexInfo()).extracting(IndexInfo::getName)
                .contains("idx_medical_records_patient_history", "idx_medical_records_doctor_history");
        var request = mapper.treeToValue(createBody(ClinicalType.PRESCRIPTION), CreatePrescriptionRequest.class);
        assertThat(request.toString()).doesNotContain("Test clinical notes", "Test medicine", "Test dosage");
        assertThat(request.medicines().get(0).toString()).doesNotContain("Test medicine", "Test dosage");
        var response = create(ClinicalType.PRESCRIPTION);
        assertThat(prescriptionServiceResponseString(response.get("id").asText())).doesNotContain("Test clinical notes");
    }

    @Test
    void serviceCreationAlsoRejectsPatientCallers() throws Exception {
        var prescription = mapper.treeToValue(createBody(ClinicalType.PRESCRIPTION), CreatePrescriptionRequest.class);
        var record = mapper.treeToValue(createBody(ClinicalType.MEDICAL_RECORD), CreateMedicalRecordRequest.class);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                patient.getUser().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
        try {
            assertThatThrownBy(() -> prescriptionService.create(prescription)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> recordService.create(record)).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String prescriptionServiceResponseString(String id) {
        return com.smarthealthcare.dto.PrescriptionResponse.from(prescriptions.findById(id).orElseThrow()).toString();
    }

    private JsonNode create(ClinicalType type) throws Exception {
        var response = mvc.perform(post(type.path).with(auth(doctor.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(type).toString()))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return mapper.readTree(response.getContentAsString());
    }

    private ObjectNode createBody(ClinicalType type) {
        ObjectNode body = mapper.createObjectNode();
        body.put("appointmentId", completed.getId());
        body.put("notes", "Test clinical notes");
        body.putArray("medicines").addObject().put("name", "Test medicine").put("dosage", "Test dosage")
                .put("instructions", "Test directions");
        if (type == ClinicalType.MEDICAL_RECORD) {
            body.put("patientId", patient.getId());
            body.putArray("labResults").add("Test lab metadata");
        }
        return body;
    }

    private ObjectNode updateBody(ClinicalType type, long version) {
        ObjectNode body = createBody(type);
        body.remove(List.of("patientId", "appointmentId"));
        body.put("version", version).put("notes", "Updated clinical notes");
        return body;
    }

    private Appointment appointment(Doctor owner, Patient target, AppointmentStatus state) {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
                .plusSeconds(1800L * slotOffset++);
        DoctorSlot slot = slots.saveAndFlush(new DoctorSlot(owner, start));
        var appointment = new Appointment(slot, target);
        if (state == AppointmentStatus.COMPLETED) appointment.complete();
        if (state == AppointmentStatus.CANCELLED) appointment.cancel();
        return appointments.saveAndFlush(appointment);
    }

    private User user(Role role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", HASH, role));
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

    private enum ClinicalType {
        PRESCRIPTION("/api/prescriptions"),
        MEDICAL_RECORD("/api/medical-records");

        private final String path;

        ClinicalType(String path) {
            this.path = path;
        }
    }
}
