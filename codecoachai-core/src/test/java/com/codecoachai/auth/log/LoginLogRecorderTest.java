package com.codecoachai.auth.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class LoginLogRecorderTest {

    @Mock
    private LoginLogAsyncWriter asyncWriter;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void capturesRequestAndTraceContextBeforeDelegatingToAsyncWriter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.2");
        request.addHeader("User-Agent", "Chrome Test Agent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("traceId", "trace-login-001");
        LoginLogRecorder recorder = new LoginLogRecorder(asyncWriter);

        recorder.recordSuccess(10L, "admin", "PASSWORD");

        ArgumentCaptor<LoginLogRecorder.Entry> captor =
                ArgumentCaptor.forClass(LoginLogRecorder.Entry.class);
        verify(asyncWriter).write(captor.capture());
        LoginLogRecorder.Entry entry = captor.getValue();
        assertEquals(10L, entry.userId());
        assertEquals("admin", entry.username());
        assertEquals("203.0.113.10", entry.ip());
        assertEquals("Chrome Test Agent", entry.userAgent());
        assertEquals("trace-login-001", entry.traceId());
    }
}
