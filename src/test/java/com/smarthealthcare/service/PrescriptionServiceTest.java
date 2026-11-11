package com.smarthealthcare.service;

import com.smarthealthcare.document.Medicine;
import com.smarthealthcare.document.Prescription;
import com.smarthealthcare.dto.CreatePrescriptionRequest;
import com.smarthealthcare.dto.UpdatePrescriptionRequest;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.repository.PrescriptionRepository;
import com.smarthealthcare.security.ClinicalAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    @Mock
    private PrescriptionRepository prescriptions;

    @Mock
    private ClinicalAccessService access;

    @InjectMocks
    private PrescriptionService service;

    private final List<Medicine> medicines = List.of(new Medicine("Test medicine", "Test dosage", null));

    @Test
    void createsDocumentUsingAuthorizedRelationalReferences() {
        when(access.forAppointment(4L))
                .thenReturn(new ClinicalAccessService.ClinicalContext(1L, 3L, 4L));
        when(prescriptions.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreatePrescriptionRequest(4L, medicines, " Initial notes "));

        assertThat(response.doctorId()).isEqualTo(1L);
        assertThat(response.patientId()).isEqualTo(3L);
        assertThat(response.appointmentId()).isEqualTo(4L);
        assertThat(response.notes()).isEqualTo("Initial notes");
        assertThat(response.medicines()).containsExactlyElementsOf(medicines);
    }

    @Test
    void rejectedAppointmentAuthorizationDoesNotWriteToMongo() {
        when(access.forAppointment(4L)).thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> service.create(new CreatePrescriptionRequest(4L, medicines, " Initial notes "))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(prescriptions);
    }

    @Test
    void refusesUpdatesByAnotherDoctor() {
        var document = document();
        when(prescriptions.findById("document-id")).thenReturn(Optional.of(document));
        doThrow(new AccessDeniedException("Access denied")).when(access).requireAuthor(1L);

        assertThatThrownBy(() -> service.update("document-id", new UpdatePrescriptionRequest(2L, medicines, " Updated notes "))).isInstanceOf(AccessDeniedException.class);
        verify(prescriptions, never()).save(any());
        assertThat(document.getNotes()).isEqualTo("Original notes");
    }

    @Test
    void rejectsStaleVersionsBeforeChangingDocument() {
        var document = document();
        ReflectionTestUtils.setField(document, "version", 3L);
        when(prescriptions.findById("document-id")).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.update("document-id", new UpdatePrescriptionRequest(2L, medicines, " Updated notes ")))
                .isInstanceOf(ResourceConflictException.class);
        verify(prescriptions, never()).save(any());
        assertThat(document.getNotes()).isEqualTo("Original notes");
    }

    @Test
    void authorUpdatesContentWithoutChangingOwnershipOrAppointment() {
        var document = document();
        when(prescriptions.findById("document-id")).thenReturn(Optional.of(document));
        when(prescriptions.save(document)).thenReturn(document);

        var response = service.update("document-id", new UpdatePrescriptionRequest(2L, medicines, " Updated notes "));

        verify(access).requireAuthor(1L);
        assertThat(response.doctorId()).isEqualTo(1L);
        assertThat(response.patientId()).isEqualTo(3L);
        assertThat(response.appointmentId()).isEqualTo(4L);
        assertThat(response.notes()).isEqualTo("Updated notes");
        assertThat(response.medicines()).containsExactlyElementsOf(medicines);
    }

    private Prescription document() {
        var document = new Prescription(1L, 3L, 4L, medicines, "Original notes");
        ReflectionTestUtils.setField(document, "version", 2L);
        return document;
    }
}
