package com.smarthealthcare.chatbot;

import com.smarthealthcare.dto.ChatbotRequest;
import com.smarthealthcare.dto.ChatbotResponse;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.exception.ChatbotUnavailableException;
import com.smarthealthcare.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.chatbot.mode", havingValue = "remote")
public class RemoteChatbotService implements ChatbotService {

    private final CurrentUser currentUser;
    private final RestClient client;

    public RemoteChatbotService(CurrentUser currentUser, RestClient.Builder builder,
                                @Value("${app.chatbot.service-url}") String url,
                                @Value("${app.chatbot.service-key}") String key) {
        if (key.isBlank()) {
            throw new IllegalArgumentException("CHATBOT_SERVICE_KEY is required in remote chatbot mode");
        }
        this.currentUser = currentUser;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(65));
        client = builder.baseUrl(url).requestFactory(factory)
                .defaultHeader("X-Chatbot-Service-Key", key).build();
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
    public ChatbotResponse reply(ChatbotRequest request) {
        Role role = currentUser.get().getRole();
        try {
            var response = client.post().uri("/internal/chatbot/messages")
                    .body(new ServiceRequest(role, request.message())).retrieve().body(ChatbotResponse.class);
            if (response == null || response.role() != role || response.reply() == null || response.reply().isBlank()
                    || response.model() == null || response.model().isBlank()) {
                throw new ChatbotUnavailableException();
            }
            return response;
        } catch (RestClientException exception) {
            throw new ChatbotUnavailableException();
        }
    }

    private record ServiceRequest(Role role, String message) {
        @Override
        public String toString() {
            return "ServiceRequest[role=" + role + "]";
        }
    }
}
