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
import com.codecoachai.common.security.config.InternalAuthProperties.CallerKeyRing;
import com.codecoachai.common.security.internal.TrustedRequestVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

    private static final String LEGACY_SECRET =
            "test-internal-legacy-secret-0123456789";
    private static final String CORE_SECRET =
            "test-internal-core-secret-012345678901";
    private static final String AI_SECRET =
            "test-internal-ai-secret-01234567890123";
    private static final String SEARCH_SECRET =
            "test-internal-search-secret-012345678";

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private InternalCallFilter filter;

    @BeforeEach
    void setUp() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setEnabled(true);
        properties.setSecret(LEGACY_SECRET);
        properties.setLegacySharedSecretEnabled(false);
        properties.setCallerKeyRings(Map.of(
                "codecoachai-core", keyRing(
                        CORE_SECRET,
                        "POST /inner/agent/**",
                        "POST /inner/job/**"),
                "codecoachai-ai", keyRing(
                        AI_SECRET,
                        "GET /inner/resume-job-match/reports"),
                "codecoachai-search", keyRing(
                        SEARCH_SECRET,
                        "GET /inner/questions")));
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
                "POST", "/inner/agent/job-coach", "", "codecoachai-core", "nonce-signed-0001");
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
        request.addHeader(HeaderConstants.SERVICE_NAME, "codecoachai-core");
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
                "POST", "/inner/job/run", "", "codecoachai-core", "nonce-context-001");
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
                "codecoachai-search",
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
                "codecoachai-search",
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

    @Test
    void callerKeyCannotForgeAnotherTrustedServiceName() throws Exception {
        MockHttpServletRequest request = signedInternalRequest(
                "GET",
                "/inner/agent/job-coach",
                "",
                "codecoachai-core",
                "nonce-forged-00001",
                AI_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void validCallerKeyCannotAccessAnUnauthorizedMethodOrPath() throws Exception {
        MockHttpServletRequest request = signedInternalRequest(
                "GET",
                "/inner/admin/users",
                "",
                "codecoachai-core",
                "nonce-unauthorized-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
        verifyNoInteractions(stringRedisTemplate);
    }

    private MockHttpServletRequest signedInternalRequest(
            String method,
            String path,
            String rawQuery,
            String serviceName,
            String nonce) {
        return signedInternalRequest(method, path, rawQuery, serviceName, nonce, secretFor(serviceName));
    }

    private MockHttpServletRequest signedInternalRequest(
            String method,
            String path,
            String rawQuery,
            String serviceName,
            String nonce,
            String signingSecret) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodySha256 = InternalSignatureUtils.EMPTY_BODY_SHA256;
        String payload = InternalSignatureUtils.internalRequestPayloadV2(
                method, path, rawQuery, timestamp, nonce, serviceName, bodySha256);
        String signature = InternalSignatureUtils.hmacSha256Hex(signingSecret, payload);
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

    private String secretFor(String serviceName) {
        return switch (serviceName) {
            case "codecoachai-core" -> CORE_SECRET;
            case "codecoachai-ai" -> AI_SECRET;
            case "codecoachai-search" -> SEARCH_SECRET;
            default -> throw new IllegalArgumentException("Unexpected service: " + serviceName);
        };
    }

    private static CallerKeyRing keyRing(String secret, String... permissions) {
        CallerKeyRing keyRing = new CallerKeyRing();
        keyRing.setSecrets(List.of(secret));
        keyRing.setPermissions(List.of(permissions));
        return keyRing;
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
