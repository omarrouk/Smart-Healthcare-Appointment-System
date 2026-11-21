package com.smarthealthcare.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthcare.dto.ChatbotRequest;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.exception.ChatbotUnavailableException;
import com.smarthealthcare.security.CurrentUser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteChatbotServiceTest {
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<JsonNode> received = new AtomicReference<>();
    private final AtomicReference<String> receivedKey = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private HttpServer server;
    private RemoteChatbotService service;

    @BeforeEach
    void startService() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/chatbot/messages", exchange -> {
            JsonNode request = mapper.readTree(exchange.getRequestBody());
            received.set(request);
            receivedKey.set(exchange.getRequestHeaders().getFirst("X-Chatbot-Service-Key"));
            byte[] body = ("{\"role\":\"" + request.get("role").asText()
                    + "\",\"reply\":\"Generated guidance\",\"model\":\"local-test-model\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        service = new RemoteChatbotService(currentUser, RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-service-key");
    }

    @AfterEach
    void stopService() {
        server.stop(0);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void forwardsOnlyTheAuthenticatedRoleAndMessageUsingThePrivateServiceKey(Role role) {
        when(currentUser.get()).thenReturn(new User("private-account@example.test", "private-hash", role));

        var response = service.reply(new ChatbotRequest("help"));

        assertThat(response.role()).isEqualTo(role);
        assertThat(response.model()).isEqualTo("local-test-model");
        assertThat(receivedKey.get()).isEqualTo("test-service-key");
        assertThat(received.get().size()).isEqualTo(2);
        assertThat(received.get().get("role").asText()).isEqualTo(role.name());
        assertThat(received.get().toString()).doesNotContain("private-account", "private-hash");
    }

    @Test
    void mapsServiceFailuresToASafeUnavailableError() {
        when(currentUser.get()).thenReturn(new User("user@example.test", "hash", Role.PATIENT));
        status.set(503);

        assertThatThrownBy(() -> service.reply(new ChatbotRequest("private-test-message")))
                .isInstanceOf(ChatbotUnavailableException.class)
                .hasMessage("Chatbot service is temporarily unavailable");
    }

    @Test
    void refusesRemoteModeWithoutAServiceKey() {
        assertThatThrownBy(() -> new RemoteChatbotService(currentUser, RestClient.builder(), "http://localhost", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
