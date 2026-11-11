package com.smarthealthcare.service;

import com.smarthealthcare.document.Medicine;
import com.smarthealthcare.document.MedicalRecord;
import com.smarthealthcare.dto.CreateMedicalRecordRequest;
import com.smarthealthcare.dto.UpdateMedicalRecordRequest;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.repository.MedicalRecordRepository;
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
class MedicalRecordServiceTest {

    @Mock
    private MedicalRecordRepository records;

    @Mock
    private ClinicalAccessService access;

    @InjectMocks
    private MedicalRecordService service;

    private final List<Medicine> medicines = List.of(new Medicine("Test medicine", "Test dosage", null));

    @Test
    void createsDocumentUsingAuthorizedRelationalReferences() {
        when(access.forPatient(3L, 4L))
                .thenReturn(new ClinicalAccessService.ClinicalContext(1L, 3L, 4L));
        when(records.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateMedicalRecordRequest(3L, 4L, medicines, " Initial notes ", List.of("Test result")));

        assertThat(response.doctorId()).isEqualTo(1L);
        assertThat(response.patientId()).isEqualTo(3L);
        assertThat(response.appointmentId()).isEqualTo(4L);
        assertThat(response.notes()).isEqualTo("Initial notes");
        assertThat(response.medicines()).containsExactlyElementsOf(medicines);
    }

    @Test
    void rejectedAppointmentAuthorizationDoesNotWriteToMongo() {
        when(access.forPatient(3L, 4L)).thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> service.create(new CreateMedicalRecordRequest(3L, 4L, medicines, " Initial notes ", List.of("Test result")))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(records);
    }

    @Test
    void refusesUpdatesByAnotherDoctor() {
        var document = document();
        when(records.findById("document-id")).thenReturn(Optional.of(document));
        doThrow(new AccessDeniedException("Access denied")).when(access).requireAuthor(1L);

        assertThatThrownBy(() -> service.update("document-id", new UpdateMedicalRecordRequest(2L, medicines, " Updated notes ", List.of("Updated result")))).isInstanceOf(AccessDeniedException.class);
        verify(records, never()).save(any());
        assertThat(document.getNotes()).isEqualTo("Original notes");
    }

    @Test
    void rejectsStaleVersionsBeforeChangingDocument() {
        var document = document();
        ReflectionTestUtils.setField(document, "version", 3L);
        when(records.findById("document-id")).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.update("document-id", new UpdateMedicalRecordRequest(2L, medicines, " Updated notes ", List.of("Updated result"))))
                .isInstanceOf(ResourceConflictException.class);
        verify(records, never()).save(any());
        assertThat(document.getNotes()).isEqualTo("Original notes");
    }

    @Test
    void authorUpdatesContentWithoutChangingOwnershipOrAppointment() {
        var document = document();
        when(records.findById("document-id")).thenReturn(Optional.of(document));
        when(records.save(document)).thenReturn(document);

        var response = service.update("document-id", new UpdateMedicalRecordRequest(2L, medicines, " Updated notes ", List.of("Updated result")));

        verify(access).requireAuthor(1L);
        assertThat(response.doctorId()).isEqualTo(1L);
        assertThat(response.patientId()).isEqualTo(3L);
        assertThat(response.appointmentId()).isEqualTo(4L);
        assertThat(response.notes()).isEqualTo("Updated notes");
        assertThat(response.medicines()).containsExactlyElementsOf(medicines);
    }

    private MedicalRecord document() {
        var document = new MedicalRecord(1L, 3L, 4L, medicines, "Original notes", List.of("Original result"));
        ReflectionTestUtils.setField(document, "version", 2L);
        return document;
    }
}
