package com.smarthealthcare.chatbotservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"chatbot.service-key=test-service-key", "chatbot.openai-api-key=test-provider-key", "chatbot.models.admin=admin-model",
        "chatbot.models.doctor=doctor-model", "chatbot.models.patient=patient-model"})
@AutoConfigureMockMvc
class ChatbotIntegrationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicReference<JsonNode> received = new AtomicReference<>();
    private static final AtomicInteger providerStatus = new AtomicInteger(200);
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final AtomicReference<String> providerBody = new AtomicReference<>();
    private static HttpServer provider;
    @Autowired private MockMvc mvc;

    @DynamicPropertySource
    static void startProvider(DynamicPropertyRegistry registry) throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v1/chat/completions", exchange -> {
            received.set(MAPPER.readTree(exchange.getRequestBody()));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = providerBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(providerStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        provider.start();
        registry.add("chatbot.openai-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1");
    }

    @BeforeEach
    void resetProvider() {
        received.set(null);
        authorization.set(null);
        providerBody.set("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Generated workflow guidance\"}}]}");
        providerStatus.set(200);
    }

    @AfterAll
    static void stopProvider() {
        provider.stop(0);
    }

    @ParameterizedTest
    @EnumSource(ChatRequest.Role.class)
    void selectsAnActualConfiguredModelAndCallsTheProvider(ChatRequest.Role role) throws Exception {
        String model = role.name().toLowerCase(Locale.ROOT) + "-model";
        mvc.perform(post("/internal/chatbot/messages").header("X-Chatbot-Service-Key", "test-service-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + role + "\",\"message\":\"help\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.model").value(model))
                .andExpect(jsonPath("$.reply").value("Generated workflow guidance"));
        assertThat(received.get().get("model").asText()).isEqualTo(model);
        assertThat(received.get().get("stream").asBoolean()).isFalse();
        assertThat(received.get().get("max_completion_tokens").asInt()).isEqualTo(256);
        assertThat(received.get().get("store").asBoolean()).isFalse();
        assertThat(authorization.get()).isEqualTo("Bearer test-provider-key");
        assertThat(received.get().toString()).doesNotContain("test-service-key", "test-provider-key");
        assertThat(received.get().get("messages").get(0).get("role").asText()).isEqualTo("system");
    }

    @Test
    void requiresThePrivateServiceKeyBeforeCallingTheModel() throws Exception {
        mvc.perform(post("/internal/chatbot/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"message\":\"help\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/chatbot/messages").header("X-Chatbot-Service-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"message\":\"help\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(received.get()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"role\":\"ROOT\",\"message\":\"help\"}", "{\"role\":\"ADMIN\",\"message\":\" \"}"})
    void validatesRoleAndMessage(String body) throws Exception {
        mvc.perform(post("/internal/chatbot/messages").header("X-Chatbot-Service-Key", "test-service-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(received.get()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 503})
    void providerFailureReturnsUnavailableInsteadOfASuccessfulStub(int code) throws Exception {
        providerStatus.set(code);
        mvc.perform(post("/internal/chatbot/messages").header("X-Chatbot-Service-Key", "test-service-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"PATIENT\",\"message\":\"help\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"choices\":[]}",
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null}}]}",
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\" \"}}]}"})
    void rejectsMissingOrEmptyProviderReplies(String body) throws Exception {
        providerBody.set(body);
        mvc.perform(post("/internal/chatbot/messages").header("X-Chatbot-Service-Key", "test-service-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"PATIENT\",\"message\":\"help\"}"))
                .andExpect(status().isServiceUnavailable());
    }

}
