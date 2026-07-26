package com.codecoachai.common.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.common.security.config.InternalAuthProperties;
import com.codecoachai.common.security.internal.TrustedRequestVerifier;
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
        properties.setMaxSignedBodyBytes(1024 * 1024);
        filter = new InternalCallFilter(
                properties,
                new TrustedRequestVerifier(properties, stringRedisTemplate));
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
        MockHttpServletRequest request = signedInternalRequest(
                "POST", "/inner/agent/job-coach", "", "codecoachai-task", "nonce-signed-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                anyString(),
                eq("1"),
                any(Duration.class))).thenReturn(true);

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
        MockHttpServletRequest request = signedInternalRequest(
                "GET",
                "/inner/resume-job-match/reports",
                "",
                "codecoachai-ai",
                "nonce-replayed-001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                anyString(),
                eq("1"),
                any(Duration.class))).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
    }

    @Test
    void canonicalizedInternalRequestWithContextPathPasses() throws Exception {
        MockHttpServletRequest request = signedInternalRequest(
                "POST", "/inner/job/run", "", "codecoachai-task", "nonce-context-001");
        request.setContextPath("/api");
        request.setRequestURI("/api//inner/%2E/task/../job/run/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                anyString(),
                eq("1"),
                any(Duration.class))).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertTrue(chain.called());
        assertEquals(200, response.getStatus());
    }

    @Test
    void encodedInnerPrefixCannotBypassInternalAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/%69nner/job/run");
        request.setContextPath("/api");
        request.setRequestURI("/api/%69nner/job/run");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void encodedTraversalDoesNotTriggerInternalAuthAfterCanonicalization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/inner/%2e%2e/admin/users");
        request.setContextPath("/api");
        request.setRequestURI("/api/inner/%2e%2e/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called());
        assertEquals(200, response.getStatus());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void queryTamperingFailsBeforeNonceIsConsumed() throws Exception {
        MockHttpServletRequest request = signedInternalRequest(
                "GET",
                "/inner/questions",
                "b=2&a=1",
                "codecoachai-question",
                "nonce-query-00001");
        request.setQueryString("b=3&a=1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void redisFailureFailsClosedWithServiceUnavailable() throws Exception {
        MockHttpServletRequest request = signedInternalRequest(
                "GET",
                "/inner/questions",
                "",
                "codecoachai-question",
                "nonce-redis-00001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(503, response.getStatus());
    }

    private MockHttpServletRequest signedInternalRequest(
            String method,
            String path,
            String rawQuery,
            String serviceName,
            String nonce) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodySha256 = InternalSignatureUtils.EMPTY_BODY_SHA256;
        String payload = InternalSignatureUtils.internalRequestPayloadV2(
                method, path, rawQuery, timestamp, nonce, serviceName, bodySha256);
        String signature = InternalSignatureUtils.hmacSha256Hex(SECRET, payload);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setQueryString(rawQuery);
        request.addHeader(HeaderConstants.INTERNAL_CALL, "true");
        request.addHeader(HeaderConstants.SERVICE_NAME, serviceName);
        request.addHeader(HeaderConstants.INTERNAL_TIMESTAMP, timestamp);
        request.addHeader(HeaderConstants.INTERNAL_NONCE, nonce);
        request.addHeader(HeaderConstants.INTERNAL_BODY_SHA256, bodySha256);
        request.addHeader(HeaderConstants.INTERNAL_SIGNATURE_V2, signature);
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
