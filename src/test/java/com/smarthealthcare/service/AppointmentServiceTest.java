package com.smarthealthcare.service;

import com.smarthealthcare.dto.BookAppointmentRequest;
import com.smarthealthcare.entity.Appointment;
import com.smarthealthcare.entity.AppointmentStatus;
import com.smarthealthcare.entity.Doctor;
import com.smarthealthcare.entity.DoctorSlot;
import com.smarthealthcare.entity.Patient;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.repository.AppointmentRepository;
import com.smarthealthcare.repository.DoctorRepository;
import com.smarthealthcare.repository.DoctorSlotRepository;
import com.smarthealthcare.repository.PatientRepository;
import com.smarthealthcare.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointments;
    @Mock private DoctorSlotRepository slots;
    @Mock private PatientRepository patients;
    @Mock private DoctorRepository doctors;
    @Mock private CurrentUser currentUser;

    private AppointmentService service;
    private Patient patient;
    private DoctorSlot slot;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointments, slots, patients, doctors, currentUser);
        var user = new User("patient@example.com", "test-hash", Role.PATIENT);
        ReflectionTestUtils.setField(user, "id", 1L);
        patient = new Patient(user, "Patient", null);
        ReflectionTestUtils.setField(patient, "id", 2L);
        var doctor = new Doctor(new User("doctor@example.com", "test-hash", Role.DOCTOR), "Doctor", "Cardiology");
        ReflectionTestUtils.setField(doctor, "id", 3L);
        slot = new DoctorSlot(doctor, Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS));
        ReflectionTestUtils.setField(slot, "id", 4L);
        when(currentUser.get()).thenReturn(user);
        when(patients.findByUserId(1L)).thenReturn(Optional.of(patient));
        when(slots.findById(4L)).thenReturn(Optional.of(slot));
    }

    @Test
    void booksAvailableSlotForTheAuthenticatedPatient() {
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.book(new BookAppointmentRequest(4L));

        assertThat(response.patientId()).isEqualTo(patient.getId());
        assertThat(response.slotId()).isEqualTo(slot.getId());
        assertThat(response.doctorId()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.endTime()).isEqualTo(slot.getStartTime().plus(DoctorSlot.DURATION));
    }

    @Test
    void rejectsAnOccupiedSlotWithoutSaving() {
        when(appointments.existsBySlotIdAndStatusNot(4L, AppointmentStatus.CANCELLED)).thenReturn(true);

        assertThatThrownBy(() -> service.book(new BookAppointmentRequest(4L)))
                .isInstanceOf(ResourceConflictException.class).hasMessage("Slot is already booked");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void databaseConstraintFailureIsNotSwallowedAfterAConcurrentBooking() {
        when(appointments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("occupied slot"));

        assertThatThrownBy(() -> service.book(new BookAppointmentRequest(4L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsPastSlots() {
        ReflectionTestUtils.setField(slot, "startTime", Instant.now().minusSeconds(1800));

        assertThatThrownBy(() -> service.book(new BookAppointmentRequest(4L)))
                .isInstanceOf(ResourceConflictException.class).hasMessage("Slot is no longer available");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSlotsBelongingToDisabledDoctors() {
        slot.getDoctor().getUser().setEnabled(false);

        assertThatThrownBy(() -> service.book(new BookAppointmentRequest(4L)))
                .isInstanceOf(ResourceConflictException.class);
        verify(appointments, never()).saveAndFlush(any());
    }
}
