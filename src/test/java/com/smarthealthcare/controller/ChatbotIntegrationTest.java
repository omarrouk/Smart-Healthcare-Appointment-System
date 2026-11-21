package com.smarthealthcare.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthcare.chatbot.ChatbotService;
import com.smarthealthcare.dto.ChatbotRequest;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import com.smarthealthcare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.bootstrap.admin.enabled=false", "app.chatbot.mode=stub"})
@AutoConfigureMockMvc
@Transactional
class ChatbotIntegrationTest {

    private static final String PASSWORD = "test-only-password";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    private static final String URL = "/api/chatbot/messages";

    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final UserRepository users;
    private final ChatbotService chatbot;
    private final Map<Role, User> accounts = new EnumMap<>(Role.class);

    @Autowired
    ChatbotIntegrationTest(MockMvc mvc, ObjectMapper mapper, UserRepository users, ChatbotService chatbot) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.users = users;
        this.chatbot = chatbot;
    }

    @BeforeEach
    void createAccounts() {
        for (Role role : Role.values()) {
            accounts.put(role, users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", HASH, role)));
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void allSupportedRolesReceiveHelpForTheirOwnWorkflows(Role role) throws Exception {
        String message = "help";
        String expected = switch (role) {
            case ADMIN -> "register patients";
            case DOCTOR -> "complete a booked appointment";
            case PATIENT -> "book or cancel your appointments";
        };
        mvc.perform(post(URL).with(auth(role)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ChatbotRequest(message))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.reply").value(containsString(expected)));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void roleAndInstructionsInTheBodyCannotOverrideAuthenticatedRole(Role role) throws Exception {
        mvc.perform(post(URL).with(auth(role)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"help\",\"role\":\"ADMIN\",\"model\":\"external\",\"patientId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.model").value("stub")).andExpect(jsonPath("$.patientId").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"message\":null}", "{\"message\":\"\"}", "{\"message\":\"   \"}"})
    void blankMessagesReturnFieldValidationErrors(String body) throws Exception {
        mvc.perform(post(URL).with(auth(Role.PATIENT)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.message").exists());
    }

    @Test
    void enforcesTheMessageLengthBoundary() throws Exception {
        mvc.perform(post(URL).with(auth(Role.PATIENT)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ChatbotRequest("x".repeat(1000)))))
                .andExpect(status().isOk());
        mvc.perform(post(URL).with(auth(Role.PATIENT)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ChatbotRequest("x".repeat(1001)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.message").exists());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void chatbotDoesNotEchoSubmittedPrivateContentOrReturnAccountDetails(Role role) throws Exception {
        String message = "private-test-content-8792";
        var response = mvc.perform(post(URL).with(auth(role)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ChatbotRequest(message))))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(message))))
                .andExpect(content().string(not(containsString(accounts.get(role).getEmail()))))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn().getResponse();
        assertThat(mapper.readTree(response.getContentAsString()).size()).isEqualTo(3);
    }

    @Test
    void csrfDoesNotGrantAccessWithoutAuthentication() throws Exception {
        mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"help\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingAndInvalidCsrfTokens() throws Exception {
        mvc.perform(post(URL).with(auth(Role.PATIENT)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"help\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("CSRF token is missing or invalid"));
        mvc.perform(post(URL).with(auth(Role.PATIENT)).with(csrf()).header("X-XSRF-TOKEN", "invalid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"help\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountAndWrongPasswordCannotUseTheChatbot() throws Exception {
        User patient = accounts.get(Role.PATIENT);
        patient.setEnabled(false);
        users.flush();
        mvc.perform(post(URL).with(auth(Role.PATIENT)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"help\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).with(httpBasic(accounts.get(Role.ADMIN).getEmail(), "wrong-test-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"help\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJsonReturnsAConsistentBadRequest() throws Exception {
        mvc.perform(post(URL).with(auth(Role.PATIENT)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request body"));
    }

    @Test
    void unsupportedChatbotRoutesRemainDenied() throws Exception {
        mvc.perform(get(URL).with(auth(Role.ADMIN))).andExpect(status().isForbidden());
        mvc.perform(post("/api/chatbot/unknown").with(auth(Role.ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"help\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceRejectsCallersWithoutASupportedRole() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                accounts.get(Role.PATIENT).getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_UNKNOWN"))));
        try {
            assertThatThrownBy(() -> chatbot.reply(new ChatbotRequest("help"))).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private RequestPostProcessor auth(Role role) {
        return httpBasic(accounts.get(role).getEmail(), PASSWORD);
    }

    private RequestPostProcessor csrf() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").with(auth(Role.ADMIN)))
                .andExpect(status().isOk()).andReturn().getResponse();
        var token = mapper.readTree(response.getContentAsString());
        MockCookie cookie = MockCookie.parse(response.getHeader(HttpHeaders.SET_COOKIE));
        return request -> {
            request.setCookies(cookie);
            request.addHeader(token.get("headerName").asText(), token.get("token").asText());
            return request;
        };
    }
}
