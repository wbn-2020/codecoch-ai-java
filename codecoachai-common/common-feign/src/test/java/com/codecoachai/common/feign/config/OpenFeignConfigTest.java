package com.codecoachai.common.feign.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class OpenFeignConfigTest {

    private static final String SECRET = "open-feign-signature-secret";
    private static final String SERVICE_NAME = "codecoachai-core";

    private final OpenFeignConfig config = new OpenFeignConfig();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void interceptorSignsCanonicalTargetBufferedBodyAndForwardedUserContext() {
        byte[] body = "{\"questionId\":42}".getBytes(StandardCharsets.UTF_8);
        RequestTemplate template = new RequestTemplate()
                .method(Request.HttpMethod.POST)
                .target("http://example.test")
                .uri("/inner/questions?b=2&a=1")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HeaderConstants.USER_ID, "999")
                .header(HeaderConstants.INTERNAL_SIGNATURE_V2, "spoofed")
                .body(body, StandardCharsets.UTF_8);
        MockHttpServletRequest inbound = new MockHttpServletRequest("POST", "/questions");
        inbound.addHeader(HeaderConstants.AUTHORIZATION, "Bearer token");
        inbound.addHeader(HeaderConstants.USER_ID, "42");
        inbound.addHeader(HeaderConstants.USERNAME, "alice");
        inbound.addHeader(HeaderConstants.ROLES, "ADMIN,USER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        RequestInterceptor interceptor =
                config.codeCoachAiFeignRequestInterceptor(SERVICE_NAME, true, SECRET);
        interceptor.apply(template);

        String bodySha256 = firstHeader(template, HeaderConstants.INTERNAL_BODY_SHA256);
        assertEquals("42", firstHeader(template, HeaderConstants.USER_ID));
        assertEquals(InternalSignatureUtils.sha256Hex(body), bodySha256);
        assertNotEquals("spoofed", firstHeader(template, HeaderConstants.INTERNAL_SIGNATURE_V2));

        String timestamp = firstHeader(template, HeaderConstants.INTERNAL_TIMESTAMP);
        String nonce = firstHeader(template, HeaderConstants.INTERNAL_NONCE);
        String internalPayload = InternalSignatureUtils.internalRequestPayloadV2(
                template.method(),
                template.path(),
                InternalSignatureUtils.rawQueryFromTarget(template.url()),
                timestamp,
                nonce,
                SERVICE_NAME,
                bodySha256);
        assertEquals(
                InternalSignatureUtils.hmacSha256Hex(SECRET, internalPayload),
                firstHeader(template, HeaderConstants.INTERNAL_SIGNATURE_V2));
        assertTrue(firstHeader(template, HeaderConstants.INTERNAL_SIGNATURE).length() == 64);

        String userTimestamp = firstHeader(template, HeaderConstants.USER_CONTEXT_TIMESTAMP);
        String userNonce = firstHeader(template, HeaderConstants.USER_CONTEXT_NONCE);
        String userPayload = InternalSignatureUtils.userContextPayloadV2(
                template.method(),
                template.path(),
                InternalSignatureUtils.rawQueryFromTarget(template.url()),
                userTimestamp,
                userNonce,
                SERVICE_NAME,
                bodySha256,
                "42",
                "alice",
                "ADMIN,USER");
        assertEquals(SERVICE_NAME, firstHeader(template, HeaderConstants.USER_CONTEXT_SIGNER));
        assertEquals(
                InternalSignatureUtils.hmacSha256Hex(SECRET, userPayload),
                firstHeader(template, HeaderConstants.USER_CONTEXT_SIGNATURE_V2));
        assertTrue(firstHeader(template, HeaderConstants.USER_CONTEXT_SIGNATURE).length() == 64);
    }

    @Test
    void multipartUsesExplicitStreamingSentinelInsteadOfHashingLeaseTokenBody() {
        byte[] leaseTokenBody = "multipart-lease-token".getBytes(StandardCharsets.UTF_8);
        RequestTemplate template = new RequestTemplate()
                .method(Request.HttpMethod.POST)
                .target("http://example.test")
                .uri("/inner/files/upload")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=test")
                .body(leaseTokenBody, StandardCharsets.UTF_8);

        config.codeCoachAiFeignRequestInterceptor(SERVICE_NAME, true, SECRET).apply(template);

        assertEquals(
                InternalSignatureUtils.STREAMING_BODY_SHA256,
                firstHeader(template, HeaderConstants.INTERNAL_BODY_SHA256));
        assertNotEquals(
                InternalSignatureUtils.sha256Hex(leaseTokenBody),
                firstHeader(template, HeaderConstants.INTERNAL_BODY_SHA256));
        assertEquals(
                "multipart-lease-token",
                new String(template.body(), StandardCharsets.UTF_8));
    }

    private String firstHeader(RequestTemplate template, String expectedName) {
        return firstHeader(template.headers(), expectedName);
    }

    private String firstHeader(Map<String, Collection<String>> headers, String expectedName) {
        return headers.entrySet().stream()
                .filter(entry -> expectedName.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse("");
    }
}
