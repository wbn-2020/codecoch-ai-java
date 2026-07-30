package com.codecoachai.common.security.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.common.security.config.InternalAuthProperties;
import com.codecoachai.common.security.internal.TrustedRequestVerifier.FailureReason;
import com.codecoachai.common.security.internal.TrustedRequestVerifier.VerificationException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class TrustedRequestVerifierTest {

    private static final String SECRET = "trusted-request-verifier-secret";
    private static final long NOW = 1_800_000_000_000L;
    private static final String NONCE = "nonce-1234567890";

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private TrustedRequestVerifier verifier;

    @BeforeEach
    void setUp() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setEnabled(true);
        properties.setSecret(SECRET);
        properties.setLegacySharedSecretCallers(Set.of("codecoachai-task", "codecoachai-gateway"));
        properties.setAllowedClockSkewSeconds(300);
        properties.setNonceTtlSeconds(300);
        properties.setMaxSignedBodyBytes(1024);
        verifier = new TrustedRequestVerifier(properties, stringRedisTemplate, () -> NOW);
    }

    @Test
    void validBufferedBodyIsVerifiedAndRemainsRepeatable() throws Exception {
        byte[] body = "{\"value\":42}".getBytes();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/inner/items");
        request.setContent(body);
        String bodySha256 = InternalSignatureUtils.sha256Hex(body);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(301))))
                .thenReturn(true);

        HttpServletRequest verified = verifier.verify(
                request,
                "codecoachai-task",
                String.valueOf(NOW),
                NONCE,
                signature("payload"),
                "internal-request:codecoachai-task",
                "payload",
                bodySha256,
                false);

        assertArrayEquals(body, verified.getInputStream().readAllBytes());
        assertArrayEquals(body, verified.getInputStream().readAllBytes());
    }

    @Test
    void invalidSignatureDoesNotReadBodyOrConsumeNonce() {
        FailingBodyRequest request = new FailingBodyRequest();

        VerificationException exception = assertThrows(
                VerificationException.class,
                () -> verifier.verify(
                        request,
                        "codecoachai-task",
                        String.valueOf(NOW),
                        NONCE,
                        "invalid",
                        "internal-request:codecoachai-task",
                        "payload",
                        InternalSignatureUtils.EMPTY_BODY_SHA256,
                        false));

        assertEquals(FailureReason.INVALID_SIGNATURE, exception.reason());
        assertFalse(request.bodyRead());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void streamingPayloadUsesExplicitSentinelWithoutReadingBody() {
        FailingBodyRequest request = new FailingBodyRequest();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(301))))
                .thenReturn(true);

        HttpServletRequest verified = verifier.verify(
                request,
                "codecoachai-gateway",
                String.valueOf(NOW),
                NONCE,
                signature("payload"),
                "user-context:codecoachai-gateway",
                "payload",
                InternalSignatureUtils.STREAMING_BODY_SHA256,
                true);

        assertSame(request, verified);
        assertFalse(request.bodyRead());
    }

    @Test
    void streamingSentinelIsRejectedWhenCallerPolicyDoesNotAllowIt() {
        FailingBodyRequest request = new FailingBodyRequest();

        VerificationException exception = assertThrows(
                VerificationException.class,
                () -> verifier.verify(
                        request,
                        "codecoachai-task",
                        String.valueOf(NOW),
                        NONCE,
                        signature("payload"),
                        "internal-request:codecoachai-task",
                        "payload",
                        InternalSignatureUtils.STREAMING_BODY_SHA256,
                        false));

        assertEquals(FailureReason.INVALID_SIGNATURE, exception.reason());
        assertFalse(request.bodyRead());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void redisFailureFailsClosedAsDependencyUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/inner/items");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(301))))
                .thenThrow(new IllegalStateException("redis unavailable"));

        VerificationException exception = assertThrows(
                VerificationException.class,
                () -> verifier.verify(
                        request,
                        "codecoachai-task",
                        String.valueOf(NOW),
                        NONCE,
                        signature("payload"),
                        "internal-request:codecoachai-task",
                        "payload",
                        InternalSignatureUtils.EMPTY_BODY_SHA256,
                        false));

        assertEquals(FailureReason.REPLAY_STORE_UNAVAILABLE, exception.reason());
    }

    @Test
    void nonceTtlCoversTheWholeRemainingTimestampWindow() {
        long futureTimestamp = NOW + Duration.ofSeconds(299).toMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/inner/items");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(true);

        verifier.verify(
                request,
                "codecoachai-task",
                String.valueOf(futureTimestamp),
                NONCE,
                signature("payload"),
                "internal-request:codecoachai-task",
                "payload",
                InternalSignatureUtils.EMPTY_BODY_SHA256,
                false);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).setIfAbsent(anyString(), eq("1"), ttlCaptor.capture());
        assertEquals(Duration.ofSeconds(600), ttlCaptor.getValue());
    }

    private String signature(String payload) {
        return InternalSignatureUtils.hmacSha256Hex(SECRET, payload);
    }

    private static final class FailingBodyRequest extends MockHttpServletRequest {

        private final AtomicBoolean bodyRead = new AtomicBoolean(false);

        private FailingBodyRequest() {
            super("POST", "/inner/items");
        }

        @Override
        public ServletInputStream getInputStream() {
            bodyRead.set(true);
            throw new AssertionError("streaming body must not be read by signature verification");
        }

        private boolean bodyRead() {
            return bodyRead.get();
        }
    }
}
