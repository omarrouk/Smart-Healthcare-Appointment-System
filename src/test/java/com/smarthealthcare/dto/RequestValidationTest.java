package com.smarthealthcare.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidDoctorAndPatientRequests() {
        var account = new UserRegistrationRequest("person@example.com", "test-only-password");

        assertThat(validator.validate(new CreateDoctorRequest(account, "Doctor", "Cardiology")))
                .isEmpty();
        assertThat(validator.validate(new CreatePatientRequest(account, "Patient", null)))
                .isEmpty();
    }

    @Test
    void validatesNestedAccountAndDoctorFields() {
        var request = new CreateDoctorRequest(
                new UserRegistrationRequest("invalid-email", "short"), " ", "");

        Set<String> invalidFields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(invalidFields).containsExactlyInAnyOrder(
                "account.email", "account.password", "name", "specialty");
    }

    @Test
    void requiresAccountAndLimitsPatientFieldLengths() {
        var request = new CreatePatientRequest(null, "x".repeat(151), "1".repeat(31));

        assertThat(validator.validate(request)).hasSize(3);
    }

    @Test
    void rejectsPasswordsExceedingBcryptByteLimit() {
        var request = new UserRegistrationRequest("person@example.com", "é".repeat(40));

        assertThat(validator.validate(request)).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("passwordWithinByteLimit"));
    }

    @Test
    void doesNotSerializeOrPrintRegistrationPassword() throws Exception {
        var mapper = new ObjectMapper();
        var request = mapper.readValue(
                "{\"email\":\"person@example.com\",\"password\":\"test-only-password\"}",
                UserRegistrationRequest.class);

        assertThat(request.password()).isEqualTo("test-only-password");
        assertThat(mapper.readTree(mapper.writeValueAsString(request)).has("password")).isFalse();
        assertThat(request.toString()).doesNotContain(request.password());
        assertThat(new CreateDoctorRequest(request, "Doctor", "Cardiology").toString())
                .doesNotContain(request.password());
    }

    @Test
    void doesNotSerializeUserPasswordHash() throws Exception {
        var user = new User("person@example.com",
                new BCryptPasswordEncoder().encode("test-only-password"), Role.PATIENT);
        var json = new ObjectMapper().valueToTree(user);

        assertThat(json.has("passwordHash")).isFalse();
        assertThat(json.toString()).doesNotContain(user.getPasswordHash());
    }

    @Test
    void csrfTokenIsAvailableToClientWithoutAppearingInToString() {
        var response = new CsrfTokenResponse("X-XSRF-TOKEN", "test-only-csrf-token");
        var json = new ObjectMapper().valueToTree(response);

        assertThat(json.get("token").asText()).isEqualTo(response.token());
        assertThat(response.toString()).doesNotContain(response.token());
    }
}
