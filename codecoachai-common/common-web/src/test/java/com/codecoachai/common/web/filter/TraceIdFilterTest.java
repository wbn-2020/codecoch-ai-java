package com.codecoachai.common.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void storesTrimmedIncomingTraceIdAsRequestAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "  incoming-trace  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> attributeDuringChain = new AtomicReference<>();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            attributeDuringChain.set((String) servletRequest.getAttribute(
                    TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE));
            mdcDuringChain.set(MDC.get("traceId"));
        };

        filter.doFilter(request, response, chain);

        assertEquals("incoming-trace", attributeDuringChain.get());
        assertEquals("incoming-trace", mdcDuringChain.get());
        assertEquals("incoming-trace", response.getHeader("X-Trace-Id"));
        assertEquals("incoming-trace", request.getAttribute(
                TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE));
        assertNull(MDC.get("traceId"));
    }

    @Test
    void storesGeneratedTraceIdAsRequestAttributeForAsyncResponseUse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE);
        assertNotNull(traceId);
        assertEquals(16, traceId.length());
        assertEquals(traceId, response.getHeader("X-Trace-Id"));
    }
}
