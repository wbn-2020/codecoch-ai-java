package com.codecoachai.common.web.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.web.filter.TraceIdFilter;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

class ResultTraceResponseBodyAdviceTest {

    private final ResultTraceResponseBodyAdvice advice = new ResultTraceResponseBodyAdvice();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void injectsTrimmedRequestAttributeBeforeHeaderAndMdc() throws Exception {
        MDC.put("traceId", "trace-mdc");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "  trace-attribute  ");
        request.addHeader("X-Trace-Id", "trace-header");
        Result<String> body = Result.success("ok");

        Object returned = advice.beforeBodyWrite(
                body,
                returnType("wrappedResult"),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                null);

        assertSame(body, returned);
        assertEquals("trace-attribute", body.getTraceId());
    }

    @Test
    void fallsBackToTrimmedRequestHeaderThenMdc() throws Exception {
        MDC.put("traceId", "  trace-mdc  ");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "  trace-header  ");
        Result<Void> body = Result.fail(500, "failed");

        advice.beforeBodyWrite(
                body,
                returnType("objectResult"),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                null);

        assertEquals("trace-header", body.getTraceId());

        Result<Void> mdcBody = Result.fail(500, "failed");
        advice.beforeBodyWrite(
                mdcBody,
                returnType("asyncResult"),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(new MockHttpServletRequest()),
                null);

        assertEquals("trace-mdc", mdcBody.getTraceId());
    }

    @Test
    void doesNotOverrideExistingTraceId() throws Exception {
        MDC.put("traceId", "trace-mdc");
        Result<String> body = Result.success("ok");
        body.setTraceId("trace-body");

        advice.beforeBodyWrite(
                body,
                returnType("result"),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                null,
                null);

        assertEquals("trace-body", body.getTraceId());
    }

    @Test
    void leavesTraceIdMissingWhenAllSourcesAreBlank() throws Exception {
        MDC.put("traceId", " ");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, " ");
        request.addHeader("X-Trace-Id", " ");
        Result<String> body = Result.success("ok");

        advice.beforeBodyWrite(
                body,
                returnType("result"),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                null);

        assertNull(body.getTraceId());
    }

    @Test
    void supportsWrappedObjectAndAsyncReturnDeclarations() throws Exception {
        assertTrue(advice.supports(
                returnType("result"),
                MappingJackson2HttpMessageConverter.class));
        assertTrue(advice.supports(
                returnType("plain"),
                MappingJackson2HttpMessageConverter.class));
        assertTrue(advice.supports(
                returnType("wrappedResult"),
                MappingJackson2HttpMessageConverter.class));
        assertTrue(advice.supports(
                returnType("objectResult"),
                MappingJackson2HttpMessageConverter.class));
        assertTrue(advice.supports(
                returnType("asyncResult"),
                MappingJackson2HttpMessageConverter.class));
    }

    @Test
    void leavesNonResultBodiesUntouched() throws Exception {
        String body = "plain";

        Object returned = advice.beforeBodyWrite(
                body,
                returnType("plain"),
                MediaType.TEXT_PLAIN,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(new MockHttpServletRequest()),
                null);

        assertSame(body, returned);
    }

    @Test
    void isDiscoveredByCommonWebComponentScanning() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CommonWebScanConfiguration.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(ResultTraceResponseBodyAdvice.class).size());
        }
    }

    private MethodParameter returnType(String methodName) throws NoSuchMethodException {
        Method method = ControllerMethods.class.getDeclaredMethod(methodName);
        return new MethodParameter(method, -1);
    }

    private static final class ControllerMethods {

        private Result<String> result() {
            return Result.success("ok");
        }

        private String plain() {
            return "ok";
        }

        private ResponseEntity<Result<String>> wrappedResult() {
            return ResponseEntity.ok(Result.success("ok"));
        }

        private Object objectResult() {
            return Result.success("ok");
        }

        private CompletableFuture<Result<String>> asyncResult() {
            return CompletableFuture.completedFuture(Result.success("ok"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "com.codecoachai.common.web.advice")
    static class CommonWebScanConfiguration {
    }
}
