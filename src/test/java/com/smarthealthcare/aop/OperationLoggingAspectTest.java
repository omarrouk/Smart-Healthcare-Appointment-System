package com.smarthealthcare.aop;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.exception.DoubleBookingException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.util.stream.Stream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLoggingAspectTest {

    private final RequestLogContext context = mock(RequestLogContext.class);
    private final OperationLoggingAspect aspect = new OperationLoggingAspect(context);
    private final ProceedingJoinPoint invocation = mock(ProceedingJoinPoint.class);
    private final Signature signature = mock(Signature.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger("healthcare.appointments");
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void captureEvents() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        events.start();
        logger.addAppender(events);
        when(invocation.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("book");
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(events);
        logger.setLevel(previousLevel);
        events.stop();
    }

    @Test
    void returnsTheOriginalResultWithoutInspectingArgumentsOrResults() throws Throwable {
        Object result = new Object() {
            @Override
            public String toString() {
                throw new AssertionError("The aspect must not format operation results");
            }
        };
        when(invocation.proceed()).thenReturn(result);

        assertThat(aspect.appointment(invocation)).isSameAs(result);
        assertThat(events.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("outcome=ATTEMPT"))
                .anyMatch(message -> message.contains("outcome=SUCCEEDED"));
        verify(invocation, never()).getArgs();
        verify(context, never()).getRequestId();
    }

    @ParameterizedTest
    @MethodSource("failures")
    void rethrowsFailuresWithoutLoggingTheirMessagesOrStackTraces(RuntimeException failure) throws Throwable {
        when(invocation.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.appointment(invocation)).isSameAs(failure);

        assertThat(events.list).hasSize(2);
        assertThat(events.list.get(1).getFormattedMessage()).contains("errorType=" + failure.getClass().getSimpleName())
                .doesNotContain("private-failure-content", "outcome=SUCCEEDED");
        assertThat(events.list.get(1).getThrowableProxy()).isNull();
        if (failure instanceof ResourceConflictException || failure instanceof DataIntegrityViolationException
                || failure instanceof OptimisticLockingFailureException) {
            assertThat(events.list.get(1).getFormattedMessage()).contains("outcome=CONFLICT");
        }
    }

    private static Stream<RuntimeException> failures() {
        return Stream.of(new ResourceConflictException("private-failure-content"),
                new DataIntegrityViolationException("private-failure-content"),
                new OptimisticLockingFailureException("private-failure-content"),
                new AccessDeniedException("private-failure-content"),
                new IllegalStateException("private-failure-content"));
    }

    @Test
    void describesDoubleBookingWithDoctorAndUtcTimeWithoutAllowingLogInjection() throws Throwable {
        var failure = new DoubleBookingException("Dr. Smith\r\nforged-entry", Instant.parse("2030-01-07T10:00:00Z"));
        when(invocation.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.appointment(invocation)).isSameAs(failure);

        assertThat(events.list).hasSize(2);
        assertThat(events.list.get(1).getFormattedMessage())
                .contains("outcome=CONFLICT", "Double booking attempt prevented for Dr. Smith  forged-entry at 10:00 AM UTC (2030-01-07)")
                .doesNotContain("\r", "\n");
        assertThat(events.list.get(1).getThrowableProxy()).isNull();
        verify(invocation, never()).getArgs();
    }
}
