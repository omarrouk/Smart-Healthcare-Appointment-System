package com.smarthealthcare.chatbot;

import com.smarthealthcare.dto.ChatbotRequest;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.security.CurrentUser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StubChatbotServiceTest {

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private StubChatbotService chatbot;

    @ParameterizedTest
    @CsvSource({
            "ADMIN, manage doctors, /api/doctors",
            "ADMIN, register a patient, /api/patients",
            "ADMIN, create a slot, UTC half-hour boundary",
            "DOCTOR, list appointments, /api/appointments/me",
            "DOCTOR, complete a visit, /complete",
            "DOCTOR, create a prescription, completed appointments",
            "DOCTOR, update a medical record, Only the author",
            "PATIENT, find a doctor by specialty, /api/doctors?specialty=Cardiology",
            "PATIENT, HOW DO I BOOK A DOCTOR APPOINTMENT?, POST /api/appointments",
            "PATIENT, cancel my appointment, /cancel",
            "PATIENT, my prescriptions, /api/prescriptions/me",
            "PATIENT, my medical records, /api/medical-records/me",
            "PATIENT, change my profile, PUT /api/patients/me"
    })
    void selectsWorkflowGuidanceUsingTheAuthenticatedRole(Role role, String message, String expected) {
        when(currentUser.get()).thenReturn(new User("test@example.com", "test-hash", role));

        var response = chatbot.reply(new ChatbotRequest("  " + message + "  "));

        assertThat(response.role()).isEqualTo(role);
        assertThat(response.reply()).contains(expected);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void unsupportedMessagesReturnRoleHelpWithoutEchoingInput(Role role) {
        when(currentUser.get()).thenReturn(new User("test@example.com", "test-hash", role));
        String message = "private-test-content-8792";

        var response = chatbot.reply(new ChatbotRequest(message));

        assertThat(response.role()).isEqualTo(role);
        assertThat(response.reply()).startsWith("I can help you").doesNotContain(message);
        assertThat(new ChatbotRequest(message).toString()).doesNotContain(message);
        assertThat(response.toString()).doesNotContain(response.reply());
    }
}
