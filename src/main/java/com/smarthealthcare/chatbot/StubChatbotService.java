package com.smarthealthcare.chatbot;

import com.smarthealthcare.dto.ChatbotRequest;
import com.smarthealthcare.dto.ChatbotResponse;
import com.smarthealthcare.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class StubChatbotService implements ChatbotService {

    private final CurrentUser currentUser;

    public StubChatbotService(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
    public ChatbotResponse reply(ChatbotRequest request) {
        var role = currentUser.get().getRole();
        String message = request.message().strip().toLowerCase(Locale.ROOT);
        String reply = switch (role) {
            case ADMIN -> adminReply(message);
            case DOCTOR -> doctorReply(message);
            case PATIENT -> patientReply(message);
        };
        return new ChatbotResponse(role, reply);
    }

    private String adminReply(String message) {
        if (message.contains("slot") || message.contains("schedule")) {
            return "Create future 30-minute slots with POST /api/doctors/{doctorId}/slots. "
                    + "Start times must be on a UTC half-hour boundary. Only slots without appointment history can be deleted.";
        }
        if (message.contains("doctor")) {
            return "Manage doctors with /api/doctors: POST creates a doctor and login, GET lists doctors, "
                    + "and PUT or DELETE by doctor ID updates or removes an unused profile.";
        }
        if (message.contains("patient")) {
            return "Register patients with POST /api/patients. Use GET /api/patients to list profiles, "
                    + "and PUT or DELETE by patient ID to update or remove an unused profile.";
        }
        return "I can help you manage doctors, register patients, and create doctor slots. Ask about one of those tasks.";
    }

    private String doctorReply(String message) {
        if (message.contains("prescription")) {
            return "Create prescriptions with POST /api/prescriptions using one of your completed appointments. "
                    + "GET /api/prescriptions/me lists your authored prescriptions. Updates require the latest document version.";
        }
        if (message.contains("record")) {
            return "Create medical records with POST /api/medical-records for a patient you have treated in a completed appointment. "
                    + "GET /api/medical-records/me lists your authored records. Only the author can update a record.";
        }
        if (message.contains("appointment") || message.contains("complete")) {
            return "GET /api/appointments/me lists your appointments. Mark one of your booked appointments completed "
                    + "with PATCH /api/appointments/{appointmentId}/complete.";
        }
        return "I can help you view appointments, complete a booked appointment, and create or update your prescriptions and medical records.";
    }

    private String patientReply(String message) {
        if (message.contains("prescription")) {
            return "GET /api/prescriptions/me shows your prescription history. You can also read one of your prescriptions by its document ID.";
        }
        if (message.contains("record")) {
            return "GET /api/medical-records/me shows your medical record history. You can also read one of your records by its document ID.";
        }
        if (message.contains("cancel")) {
            return "Find your appointment with GET /api/appointments/me, then use PATCH /api/appointments/{appointmentId}/cancel. "
                    + "Only your own booked appointments can be cancelled.";
        }
        if (message.contains("book") || message.contains("slot") || message.contains("appointment")) {
            return "View available slots with GET /api/doctors/{doctorId}/slots, then POST /api/appointments with a slotId. "
                    + "GET /api/appointments/me lists your appointments. An occupied slot returns 409 Conflict.";
        }
        if (message.contains("doctor") || message.contains("specialty")) {
            return "Search by specialty with GET /api/doctors?specialty=Cardiology, then view available slots "
                    + "with GET /api/doctors/{doctorId}/slots.";
        }
        if (message.contains("profile")) {
            return "Read your profile with GET /api/patients/me and update your name or phone with PUT /api/patients/me.";
        }
        return "I can help you search doctors, book or cancel your appointments, update your profile, and view your prescriptions and medical records.";
    }
}
