package com.codecoachai.common.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.common.security.config.InternalAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class InternalCallFilterTest {

    private static final String SECRET = "test-internal-secret";

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private InternalCallFilter filter;

    @BeforeEach
    void setUp() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setEnabled(true);
        properties.setSecret(SECRET);
        properties.setAllowedClockSkewSeconds(300);
        properties.setNonceTtlSeconds(300);
        filter = new InternalCallFilter(properties, stringRedisTemplate);
    }

    @Test
    void nonInternalPathBypassesInternalAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called());
        assertEquals(200, response.getStatus());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void signedInternalRequestStoresNonceAndPasses() throws Exception {
        MockHttpServletRequest request = signedInternalRequest("POST", "/inner/agent/job-coach", "codecoachai-task", "n-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:internal:nonce:codecoachai-task:n-1"),
                eq("1"),
                eq(Duration.ofSeconds(300)))).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertTrue(chain.called());
        assertEquals(200, response.getStatus());
    }

    @Test
    void internalRequestWithMissingSignatureFailsClosed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/inner/agent/job-coach");
        request.addHeader(HeaderConstants.INTERNAL_CALL, "true");
        request.addHeader(HeaderConstants.SERVICE_NAME, "codecoachai-task");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
    }

    @Test
    void replayedNonceFailsClosedBeforeController() throws Exception {
        MockHttpServletRequest request = signedInternalRequest("GET", "/inner/resume-job-match/reports", "codecoachai-ai", "n-replay");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:internal:nonce:codecoachai-ai:n-replay"),
                eq("1"),
                eq(Duration.ofSeconds(300)))).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
    }

    private MockHttpServletRequest signedInternalRequest(String method, String path, String serviceName, String nonce) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String payload = InternalSignatureUtils.canonicalPayload(method, path, timestamp, nonce, serviceName);
        String signature = InternalSignatureUtils.hmacSha256Hex(SECRET, payload);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader(HeaderConstants.INTERNAL_CALL, "true");
        request.addHeader(HeaderConstants.SERVICE_NAME, serviceName);
        request.addHeader(HeaderConstants.INTERNAL_TIMESTAMP, timestamp);
        request.addHeader(HeaderConstants.INTERNAL_NONCE, nonce);
        request.addHeader(HeaderConstants.INTERNAL_SIGNATURE, signature);
        return request;
    }

    private static class RecordingFilterChain implements FilterChain {

        private final AtomicBoolean called = new AtomicBoolean(false);

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            called.set(true);
        }

        boolean called() {
            return called.get();
        }
    }
}
