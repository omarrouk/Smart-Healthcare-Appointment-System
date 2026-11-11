package com.smarthealthcare.aop;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLogContextTest {

    @Test
    void reusesTheIdWithinARequestAndCreatesAnotherForTheNextRequest() {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(RequestLogContext.class);
            context.refresh();
            var bean = context.getBean(RequestLogContext.class);
            String first;
            var firstRequest = new ServletRequestAttributes(new MockHttpServletRequest());
            RequestContextHolder.setRequestAttributes(firstRequest);
            try {
                first = bean.getRequestId();
                assertThat(bean.getRequestId()).isEqualTo(first);
            } finally {
                firstRequest.requestCompleted();
                RequestContextHolder.resetRequestAttributes();
            }
            var secondRequest = new ServletRequestAttributes(new MockHttpServletRequest());
            RequestContextHolder.setRequestAttributes(secondRequest);
            try {
                assertThat(bean.getRequestId()).isNotEqualTo(first);
            } finally {
                secondRequest.requestCompleted();
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }
}
