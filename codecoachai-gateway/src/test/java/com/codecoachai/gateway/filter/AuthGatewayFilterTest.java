package com.codecoachai.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.gateway.domain.TokenInfo;
import com.codecoachai.gateway.service.AuthTokenClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AuthGatewayFilterTest {

    private static final String SECRET = "gateway-user-context-secret";
    private static final String CORE_SECRET = "gateway-to-core-secret";
    private static final String AI_SECRET = "gateway-to-ai-secret";
    private static final String SEARCH_SECRET = "gateway-to-search-secret";

    @Mock
    private AuthTokenClient authTokenClient;

    private AuthGatewayFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthGatewayFilter(authTokenClient);
        ReflectionTestUtils.setField(filter, "internalAuthEnabled", true);
        ReflectionTestUtils.setField(filter, "internalSecret", SECRET);
        ReflectionTestUtils.setField(filter, "coreTargetSecret", CORE_SECRET);
        ReflectionTestUtils.setField(filter, "aiTargetSecret", AI_SECRET);
        ReflectionTestUtils.setField(filter, "searchTargetSecret", SEARCH_SECRET);
        ReflectionTestUtils.setField(filter, "maxSignedBodyBytes", 1024 * 1024);
        ReflectionTestUtils.setField(filter, "unsignedBodyPaths", "");
    }

    @Test
    void authenticatedBoundedRequestHashesAndReplaysBody() {
        TokenInfo tokenInfo = tokenInfo();
        when(authTokenClient.tokenInfo("Bearer valid-token"))
                .thenReturn(Mono.just(Result.success(tokenInfo)));
        String requestBody = "{\"title\":\"signed\"}";
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/questions?b=2&a=1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header(HeaderConstants.USER_CONTEXT_SIGNATURE_V2, "spoofed")
                .body(requestBody);
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
                InternalSignatureUtils.sha256Hex(requestBody.getBytes(StandardCharsets.UTF_8)),
                headers.getFirst(HeaderConstants.INTERNAL_BODY_SHA256));
        assertNotEquals("spoofed", headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNATURE_V2));
        assertEquals(requestBody, readBody(forwarded.get()));

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
                InternalSignatureUtils.hmacSha256Hex(CORE_SECRET, payload),
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
                InternalSignatureUtils.hmacSha256Hex(CORE_SECRET, changedQueryPayload));
    }

    @Test
    void coreRouteUsesCoreTargetSecret() {
        assertTargetSecret("codecoachai-core", CORE_SECRET);
    }

    @Test
    void aiRouteUsesAiTargetSecret() {
        assertTargetSecret("codecoachai-ai", AI_SECRET);
    }

    @Test
    void searchRouteUsesSearchTargetSecret() {
        assertTargetSecret("codecoachai-search", SEARCH_SECRET);
    }

    @Test
    void configuredMultipartUploadUsesUnsignedModeWithoutConsumingBody() {
        ReflectionTestUtils.setField(filter, "unsignedBodyPaths", "/files/upload");
        when(authTokenClient.tokenInfo("Bearer valid-token"))
                .thenReturn(Mono.just(Result.success(tokenInfo())));
        String requestBody = "--boundary\r\nupload-bytes\r\n--boundary--";
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/files/upload")
                .contentType(MediaType.parseMediaType("multipart/form-data;boundary=boundary"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .body(requestBody);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(
                        MockServerWebExchange.from(request),
                        captured -> {
                            forwarded.set(captured);
                            return Mono.empty();
                        })
                .block();

        assertEquals(
                InternalSignatureUtils.STREAMING_BODY_SHA256,
                forwarded.get().getRequest().getHeaders().getFirst(HeaderConstants.INTERNAL_BODY_SHA256));
        assertEquals(requestBody, readBody(forwarded.get()));
    }

    @Test
    void multipartBodyOnUnlistedPathIsHashedAndReplayed() {
        ReflectionTestUtils.setField(filter, "unsignedBodyPaths", "/files/upload");
        when(authTokenClient.tokenInfo("Bearer valid-token"))
                .thenReturn(Mono.just(Result.success(tokenInfo())));
        String requestBody = "--boundary\r\nnot-an-upload-route\r\n--boundary--";
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/questions")
                .contentType(MediaType.parseMediaType("multipart/form-data;boundary=boundary"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .body(requestBody);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(
                        MockServerWebExchange.from(request),
                        captured -> {
                            forwarded.set(captured);
                            return Mono.empty();
                        })
                .block();

        assertEquals(
                InternalSignatureUtils.sha256Hex(requestBody.getBytes(StandardCharsets.UTF_8)),
                forwarded.get().getRequest().getHeaders().getFirst(HeaderConstants.INTERNAL_BODY_SHA256));
        assertEquals(requestBody, readBody(forwarded.get()));
    }

    @Test
    void octetStreamOnListedPathIsStillHashedAndBounded() {
        ReflectionTestUtils.setField(filter, "unsignedBodyPaths", "/files/upload");
        when(authTokenClient.tokenInfo("Bearer valid-token"))
                .thenReturn(Mono.just(Result.success(tokenInfo())));
        String requestBody = "binary-content";
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/files/upload")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .body(requestBody);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(
                        MockServerWebExchange.from(request),
                        captured -> {
                            forwarded.set(captured);
                            return Mono.empty();
                        })
                .block();

        assertEquals(
                InternalSignatureUtils.sha256Hex(requestBody.getBytes(StandardCharsets.UTF_8)),
                forwarded.get().getRequest().getHeaders().getFirst(HeaderConstants.INTERNAL_BODY_SHA256));
        assertEquals(requestBody, readBody(forwarded.get()));
    }

    @Test
    void oversizedOrdinaryBodyIsRejectedInsteadOfFallingBackToUnsignedMode() {
        ReflectionTestUtils.setField(filter, "maxSignedBodyBytes", 4);
        when(authTokenClient.tokenInfo("Bearer valid-token"))
                .thenReturn(Mono.just(Result.success(tokenInfo())));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/questions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .body("12345"));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(
                        exchange,
                        captured -> {
                            forwarded.set(true);
                            return Mono.empty();
                        })
                .block();

        assertFalse(forwarded.get());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exchange.getResponse().getStatusCode());
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

    private void assertTargetSecret(String targetService, String expectedSecret) {
        when(authTokenClient.tokenInfo("Bearer valid-token"))
                .thenReturn(Mono.just(Result.success(tokenInfo())));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/route-secret-check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build());
        Route route = Route.async()
                .id(targetService)
                .uri("lb://" + targetService)
                .asyncPredicate(ignored -> Mono.just(true))
                .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(
                        exchange,
                        captured -> {
                            forwarded.set(captured);
                            return Mono.empty();
                        })
                .block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        String payload = InternalSignatureUtils.userContextPayloadV2(
                "GET",
                "/route-secret-check",
                null,
                headers.getFirst(HeaderConstants.USER_CONTEXT_TIMESTAMP),
                headers.getFirst(HeaderConstants.USER_CONTEXT_NONCE),
                headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNER),
                headers.getFirst(HeaderConstants.INTERNAL_BODY_SHA256),
                headers.getFirst(HeaderConstants.USER_ID),
                headers.getFirst(HeaderConstants.USERNAME),
                headers.getFirst(HeaderConstants.ROLES));
        assertEquals(
                InternalSignatureUtils.hmacSha256Hex(expectedSecret, payload),
                headers.getFirst(HeaderConstants.USER_CONTEXT_SIGNATURE_V2));
    }

    private String readBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return new String(bytes, StandardCharsets.UTF_8);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .defaultIfEmpty("")
                .block();
    }
}
