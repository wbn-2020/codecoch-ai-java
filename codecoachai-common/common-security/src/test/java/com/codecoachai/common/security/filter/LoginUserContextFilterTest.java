package com.codecoachai.common.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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
class LoginUserContextFilterTest {

    private static final String LEGACY_SECRET =
            "test-user-context-legacy-secret-01234567";
    private static final String GATEWAY_SECRET =
            "test-user-context-gateway-secret-012345";
    private static final String AI_SECRET =
            "test-user-context-ai-secret-0123456789";

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginUserContextFilter filter;

    @BeforeEach
    void setUp() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setSecret(LEGACY_SECRET);
        properties.setLegacySharedSecretEnabled(false);
        properties.setCallerKeyRings(Map.of(
                "codecoachai-gateway", keyRing(GATEWAY_SECRET, true),
                "codecoachai-ai", keyRing(AI_SECRET, false)));
        properties.setAllowedClockSkewSeconds(300);
        properties.setNonceTtlSeconds(300);
        properties.setMaxSignedBodyBytes(1024 * 1024);
        filter = new LoginUserContextFilter(
                properties,
                new TrustedRequestVerifier(properties, stringRedisTemplate));
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void validSignedUserContextIsAvailableInsideChainAndClearedAfterwards() throws Exception {
        MockHttpServletRequest request = signedUserRequest("GET", "/resume/profile", "10", "alice", "ROLE_ADMIN,USER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(true);

        filter.doFilter(request, response, chain);

        assertTrue(chain.called());
        assertEquals(10L, chain.userId());
        assertEquals("alice", chain.username());
        assertEquals(List.of("ROLE_ADMIN", "USER"), chain.roles());
        assertNull(LoginUserContext.getLoginUser());
        assertEquals(200, response.getStatus());
    }

    @Test
    void missingUserContextSignatureFailsClosedAndClearsStaleContext() throws Exception {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(99L).roles(List.of("ADMIN")).build());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/resume/profile");
        request.addHeader(HeaderConstants.USER_ID, "10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
        assertNull(LoginUserContext.getLoginUser());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void missingUserIdClearsStaleContextAndContinuesAsAnonymous() throws Exception {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(99L).roles(List.of("ADMIN")).build());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called());
        assertNull(chain.userId());
        assertNull(LoginUserContext.getLoginUser());
        assertEquals(200, response.getStatus());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void replayedUserContextNonceFailsClosed() throws Exception {
        MockHttpServletRequest request = signedUserRequest(
                "GET", "/resume/profile", "10", "alice", "ROLE_ADMIN,USER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(false);

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
        assertNull(LoginUserContext.getLoginUser());
    }

    @Test
    void userContextReplayStoreFailureReturnsServiceUnavailable() throws Exception {
        MockHttpServletRequest request = signedUserRequest(
                "GET", "/resume/profile", "10", "alice", "ROLE_ADMIN,USER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(503, response.getStatus());
        assertNull(LoginUserContext.getLoginUser());
    }

    @Test
    void serviceKeyCannotForgeGatewaySignedUserContext() throws Exception {
        MockHttpServletRequest request = signedUserRequest(
                "GET",
                "/resume/profile",
                "10",
                "alice",
                "ROLE_ADMIN,USER",
                "codecoachai-gateway",
                AI_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
        assertNull(LoginUserContext.getLoginUser());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void callerWithoutForwardingPermissionCannotAttachUserContext() throws Exception {
        MockHttpServletRequest request = signedUserRequest(
                "GET",
                "/resume/profile",
                "10",
                "alice",
                "ROLE_ADMIN,USER",
                "codecoachai-ai",
                AI_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
        assertNull(LoginUserContext.getLoginUser());
        verifyNoInteractions(stringRedisTemplate);
    }

    private MockHttpServletRequest signedUserRequest(String method, String path, String userId, String username,
                                                     String roles) {
        return signedUserRequest(
                method, path, userId, username, roles, "codecoachai-gateway", GATEWAY_SECRET);
    }

    private MockHttpServletRequest signedUserRequest(
            String method,
            String path,
            String userId,
            String username,
            String roles,
            String signer,
            String signingSecret) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-user-ctx-01";
        String bodySha256 = InternalSignatureUtils.EMPTY_BODY_SHA256;
        String payload = InternalSignatureUtils.userContextPayloadV2(
                method,
                path,
                "",
                timestamp,
                nonce,
                signer,
                bodySha256,
                userId,
                username,
                roles);
        String signature = InternalSignatureUtils.hmacSha256Hex(signingSecret, payload);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader(HeaderConstants.USER_ID, userId);
        request.addHeader(HeaderConstants.USERNAME, username);
        request.addHeader(HeaderConstants.ROLES, roles);
        request.addHeader(HeaderConstants.USER_CONTEXT_TIMESTAMP, timestamp);
        request.addHeader(HeaderConstants.USER_CONTEXT_NONCE, nonce);
        request.addHeader(HeaderConstants.USER_CONTEXT_SIGNER, signer);
        request.addHeader(HeaderConstants.INTERNAL_BODY_SHA256, bodySha256);
        request.addHeader(HeaderConstants.USER_CONTEXT_SIGNATURE_V2, signature);
        return request;
    }

    private static CallerKeyRing keyRing(String secret, boolean forwardUserContext) {
        CallerKeyRing keyRing = new CallerKeyRing();
        keyRing.setSecrets(List.of(secret));
        keyRing.setPermissions(List.of("GET /inner/test"));
        keyRing.setForwardUserContext(forwardUserContext);
        return keyRing;
    }

    private static class CapturingFilterChain implements FilterChain {

        private final AtomicBoolean called = new AtomicBoolean(false);
        private final AtomicReference<Long> userId = new AtomicReference<>();
        private final AtomicReference<String> username = new AtomicReference<>();
        private final AtomicReference<List<String>> roles = new AtomicReference<>();

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            called.set(true);
            userId.set(LoginUserContext.getUserId());
            username.set(LoginUserContext.getUsername());
            roles.set(LoginUserContext.getRoles());
        }

        boolean called() {
            return called.get();
        }

        Long userId() {
            return userId.get();
        }

        String username() {
            return username.get();
        }

        List<String> roles() {
            return roles.get();
        }
    }
}
