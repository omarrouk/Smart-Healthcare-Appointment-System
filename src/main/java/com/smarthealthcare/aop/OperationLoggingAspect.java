package com.smarthealthcare.aop;

import com.smarthealthcare.exception.InvalidRequestException;
import com.smarthealthcare.exception.ResourceConflictException;
import com.smarthealthcare.exception.ResourceNotFoundException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
// Wrap transaction advice so commit failures cannot be logged as successful writes.
@Order(0)
public class OperationLoggingAspect {

    private static final Logger appointments = LoggerFactory.getLogger("healthcare.appointments");
    private static final Logger prescriptions = LoggerFactory.getLogger("healthcare.prescriptions");

    private final RequestLogContext requestContext;

    public OperationLoggingAspect(RequestLogContext requestContext) {
        this.requestContext = requestContext;
    }

    @Around("execution(* com.smarthealthcare.service.AppointmentService.book(..)) || "
            + "execution(* com.smarthealthcare.service.AppointmentService.cancel(..)) || "
            + "execution(* com.smarthealthcare.service.AppointmentService.complete(..))")
    public Object appointment(ProceedingJoinPoint invocation) throws Throwable {
        return log(invocation, appointments);
    }

    @Around("execution(* com.smarthealthcare.service.PrescriptionService.create(..)) || "
            + "execution(* com.smarthealthcare.service.PrescriptionService.update(..))")
    public Object prescription(ProceedingJoinPoint invocation) throws Throwable {
        return log(invocation, prescriptions);
    }

    private Object log(ProceedingJoinPoint invocation, Logger logger) throws Throwable {
        String requestId = RequestContextHolder.getRequestAttributes() == null ? "none" : requestContext.getRequestId();
        String operation = invocation.getSignature().getName();
        long start = System.nanoTime();
        logger.info("requestId={} operation={} outcome=ATTEMPT", requestId, operation);
        try {
            Object result = invocation.proceed();
            logger.info("requestId={} operation={} outcome=SUCCEEDED durationMs={}", requestId, operation, elapsed(start));
            return result;
        } catch (Throwable failure) {
            logger.warn("requestId={} operation={} outcome={} errorType={} durationMs={}",
                    requestId, operation, outcome(failure), failure.getClass().getSimpleName(), elapsed(start));
            throw failure;
        }
    }

    private long elapsed(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private String outcome(Throwable failure) {
        if (failure instanceof ResourceConflictException || failure instanceof DataIntegrityViolationException
                || failure instanceof OptimisticLockingFailureException) {
            return "CONFLICT";
        }
        if (failure instanceof AccessDeniedException) return "FORBIDDEN";
        if (failure instanceof ResourceNotFoundException) return "NOT_FOUND";
        if (failure instanceof InvalidRequestException) return "INVALID_REQUEST";
        return "FAILED";
    }
}
