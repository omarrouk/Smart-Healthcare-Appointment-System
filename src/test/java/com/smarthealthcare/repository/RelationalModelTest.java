package com.smarthealthcare.repository;

import com.smarthealthcare.entity.Appointment;
import com.smarthealthcare.entity.AppointmentStatus;
import com.smarthealthcare.entity.Doctor;
import com.smarthealthcare.entity.DoctorSlot;
import com.smarthealthcare.entity.Patient;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RelationalModelTest {

    private static final Instant START_TIME = Instant.parse("2030-01-07T10:00:00Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode("test-only-password");

    private final UserRepository users;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final DoctorSlotRepository slots;
    private final AppointmentRepository appointments;
    private final EntityManager entityManager;

    @Autowired
    RelationalModelTest(UserRepository users, DoctorRepository doctors, PatientRepository patients,
                        DoctorSlotRepository slots, AppointmentRepository appointments,
                        EntityManager entityManager) {
        this.users = users;
        this.doctors = doctors;
        this.patients = patients;
        this.slots = slots;
        this.appointments = appointments;
        this.entityManager = entityManager;
    }

    @Test
    void persistsRelationshipsAndQueriesRepositories() {
        Doctor doctor = createDoctor();
        Patient patient = createPatient();
        DoctorSlot slot = slots.saveAndFlush(new DoctorSlot(doctor, START_TIME));
        Appointment appointment = appointments.saveAndFlush(new Appointment(slot, patient));
        Long appointmentId = appointment.getId();
        Long doctorId = doctor.getId();
        Long patientId = patient.getId();
        Long doctorUserId = doctor.getUser().getId();
        Long patientUserId = patient.getUser().getId();
        String email = doctor.getUser().getEmail();
        String specialty = doctor.getSpecialty();
        entityManager.clear();

        Appointment loaded = appointments.findById(appointmentId).orElseThrow();

        assertThat(loaded.getSlot().getDoctor().getId()).isEqualTo(doctorId);
        assertThat(loaded.getPatient().getId()).isEqualTo(patientId);
        assertThat(loaded.getSlot().getStartTime()).isEqualTo(START_TIME);
        assertThat(loaded.getSlot().getEndTime()).isEqualTo(START_TIME.plusSeconds(1800));
        assertThat(loaded.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(users.findByEmailIgnoreCase(email.toUpperCase(Locale.ROOT))).isPresent();
        assertThat(doctors.findByUserId(doctorUserId)).isPresent();
        assertThat(patients.findByUserId(patientUserId)).isPresent();
        assertThat(doctors.findBySpecialtyIgnoreCase(specialty.toLowerCase(Locale.ROOT)))
                .extracting(Doctor::getId).containsExactly(doctorId);
        assertThat(slots.findByDoctorIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                doctorId, START_TIME)).extracting(DoctorSlot::getId).containsExactly(slot.getId());
        assertThat(appointments.findByPatientIdOrderBySlotStartTimeAsc(patientId))
                .extracting(Appointment::getId).containsExactly(appointmentId);
        assertThat(appointments.findBySlotDoctorIdOrderBySlotStartTimeAsc(doctorId))
                .extracting(Appointment::getId).containsExactly(appointmentId);
    }

    @Test
    void rejectsDuplicateEmailAfterNormalization() {
        String email = UUID.randomUUID() + "@example.com";
        users.saveAndFlush(new User(email, PASSWORD_HASH, Role.ADMIN));

        assertThatThrownBy(() -> users.saveAndFlush(new User(
                "  " + email.toUpperCase(Locale.ROOT) + "  ", PASSWORD_HASH, Role.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateSlotsForOneDoctor() {
        Doctor doctor = createDoctor();
        slots.saveAndFlush(new DoctorSlot(doctor, START_TIME));

        assertThatThrownBy(() -> slots.saveAndFlush(new DoctorSlot(doctor, START_TIME)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTwoBookingsForTheSameSlot() {
        DoctorSlot slot = slots.saveAndFlush(new DoctorSlot(createDoctor(), START_TIME));
        Patient firstPatient = createPatient();
        Patient secondPatient = createPatient();
        appointments.saveAndFlush(new Appointment(slot, firstPatient));

        assertThatThrownBy(() -> appointments.saveAndFlush(new Appointment(slot, secondPatient)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsRebookingAfterCancellation() {
        DoctorSlot slot = slots.saveAndFlush(new DoctorSlot(createDoctor(), START_TIME));
        Patient patient = createPatient();
        Appointment cancelled = appointments.saveAndFlush(new Appointment(slot, patient));
        updateStatus(cancelled.getId(), AppointmentStatus.CANCELLED);

        Appointment replacement = appointments.saveAndFlush(new Appointment(slot, patient));

        assertThat(replacement.getId()).isNotEqualTo(cancelled.getId());
        assertThat(appointments.findByPatientIdOrderBySlotStartTimeAsc(patient.getId())).hasSize(2);
    }

    @Test
    void keepsCompletedSlotsOccupied() {
        DoctorSlot slot = slots.saveAndFlush(new DoctorSlot(createDoctor(), START_TIME));
        Patient patient = createPatient();
        Appointment completed = appointments.saveAndFlush(new Appointment(slot, patient));
        updateStatus(completed.getId(), AppointmentStatus.COMPLETED);

        assertThatThrownBy(() -> appointments.saveAndFlush(new Appointment(slot, patient)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsSlotOutsideHalfHourBoundary() {
        Doctor doctor = createDoctor();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO doctor_slots (doctor_id, start_time, end_time)
                VALUES (:doctorId, TIMESTAMPTZ '2030-01-07 10:01:00Z', TIMESTAMPTZ '2030-01-07 10:31:00Z')
                """)
                .setParameter("doctorId", doctor.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("ck_slot_alignment");
    }

    @Test
    void databaseRejectsSlotWithWrongDuration() {
        Doctor doctor = createDoctor();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO doctor_slots (doctor_id, start_time, end_time)
                VALUES (:doctorId, TIMESTAMPTZ '2030-01-07 10:00:00Z', TIMESTAMPTZ '2030-01-07 11:00:00Z')
                """)
                .setParameter("doctorId", doctor.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("ck_slot_duration");
    }

    private Doctor createDoctor() {
        User user = users.save(new User(UUID.randomUUID() + "@example.com", PASSWORD_HASH, Role.DOCTOR));
        return doctors.saveAndFlush(new Doctor(user, "Test Doctor", "Cardiology-" + UUID.randomUUID()));
    }

    private Patient createPatient() {
        User user = users.save(new User(UUID.randomUUID() + "@example.com", PASSWORD_HASH, Role.PATIENT));
        return patients.saveAndFlush(new Patient(user, "Test Patient", null));
    }

    private void updateStatus(Long appointmentId, AppointmentStatus status) {
        entityManager.createNativeQuery("UPDATE appointments SET status = :status WHERE id = :id")
                .setParameter("status", status.name())
                .setParameter("id", appointmentId)
                .executeUpdate();
        entityManager.clear();
    }
}
