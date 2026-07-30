package com.codecoachai.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.gateway.domain.TokenInfo;
import com.codecoachai.gateway.service.AuthTokenClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AuthGatewayFilterTest {

    private static final String SECRET = "gateway-user-context-secret";

    @Mock
    private AuthTokenClient authTokenClient;

    private AuthGatewayFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthGatewayFilter(authTokenClient);
        ReflectionTestUtils.setField(filter, "internalAuthEnabled", true);
        ReflectionTestUtils.setField(filter, "internalSecret", SECRET);
    }

    @Test
    void authenticatedRequestGetsNonceBoundV2UserContextWithoutBufferingBody() {
        TokenInfo tokenInfo = tokenInfo();
        when(authTokenClient.tokenInfo("Bearer valid-token"))
                .thenReturn(Mono.just(Result.success(tokenInfo)));
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/questions?b=2&a=1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header(HeaderConstants.USER_CONTEXT_SIGNATURE_V2, "spoofed")
                .body("{\"title\":\"streamed\"}");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = captured -> {
            forwarded.set(captured);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertEquals("42", headers.getFirst(HeaderConstants.USER_ID));
        assertEquals("codecoachai-gateway", headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNER));
        assertEquals(
                InternalSignatureUtils.STREAMING_BODY_SHA256,
                headers.getFirst(HeaderConstants.INTERNAL_BODY_SHA256));
        assertNotEquals("spoofed", headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNATURE_V2));

        String payload = InternalSignatureUtils.userContextPayloadV2(
                "POST",
                "/questions",
                "b=2&a=1",
                headers.getFirst(HeaderConstants.USER_CONTEXT_TIMESTAMP),
                headers.getFirst(HeaderConstants.USER_CONTEXT_NONCE),
                headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNER),
                headers.getFirst(HeaderConstants.INTERNAL_BODY_SHA256),
                headers.getFirst(HeaderConstants.USER_ID),
                headers.getFirst(HeaderConstants.USERNAME),
                headers.getFirst(HeaderConstants.ROLES));
        assertEquals(
                InternalSignatureUtils.hmacSha256Hex(SECRET, payload),
                headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNATURE_V2));

        String changedQueryPayload = InternalSignatureUtils.userContextPayloadV2(
                "POST",
                "/questions",
                "b=3&a=1",
                headers.getFirst(HeaderConstants.USER_CONTEXT_TIMESTAMP),
                headers.getFirst(HeaderConstants.USER_CONTEXT_NONCE),
                headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNER),
                headers.getFirst(HeaderConstants.INTERNAL_BODY_SHA256),
                headers.getFirst(HeaderConstants.USER_ID),
                headers.getFirst(HeaderConstants.USERNAME),
                headers.getFirst(HeaderConstants.ROLES));
        assertNotEquals(
                headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNATURE_V2),
                InternalSignatureUtils.hmacSha256Hex(SECRET, changedQueryPayload));
    }

    @Test
    void whitePathStripsAllExternallySuppliedTrustedHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/auth/login")
                .header(HeaderConstants.USER_ID, "999")
                .header(HeaderConstants.USER_CONTEXT_NONCE, "nonce-attacker-01")
                .header(HeaderConstants.USER_CONTEXT_SIGNER, "codecoachai-gateway")
                .header(HeaderConstants.USER_CONTEXT_SIGNATURE_V2, "spoofed")
                .header(HeaderConstants.INTERNAL_BODY_SHA256, "spoofed")
                .build();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(
                        MockServerWebExchange.from(request),
                        captured -> {
                            forwarded.set(captured);
                            return Mono.empty();
                        })
                .block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertNull(headers.getFirst(HeaderConstants.USER_ID));
        assertNull(headers.getFirst(HeaderConstants.USER_CONTEXT_NONCE));
        assertNull(headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNER));
        assertNull(headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNATURE_V2));
        assertNull(headers.getFirst(HeaderConstants.INTERNAL_BODY_SHA256));
    }

    @Test
    void authenticatedAdminRouteForwardsNonAdminForDownstreamPermissionCheck() {
        TokenInfo tokenInfo = tokenInfo();
        tokenInfo.setRoles(List.of("USER"));
        when(authTokenClient.tokenInfo("Bearer valid-token"))
                .thenReturn(Mono.just(Result.success(tokenInfo)));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();

        filter.filter(
                        MockServerWebExchange.from(request),
                        captured -> {
                            forwarded.set(captured);
                            return Mono.empty();
                        })
                .block();

        assertNotNull(forwarded.get());
        assertEquals("USER", forwarded.get().getRequest().getHeaders().getFirst(HeaderConstants.ROLES));
    }

    private TokenInfo tokenInfo() {
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setUserId(42L);
        tokenInfo.setUsername("alice");
        tokenInfo.setRoles(List.of("ADMIN", "USER"));
        return tokenInfo;
    }
}
