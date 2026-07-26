package com.codecoachai.common.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class InternalSignatureUtilsTest {

    @Test
    void normalizePathCanonicalizesQuerySlashAndDotSegments() {
        assertEquals("/inner/job/run",
                InternalSignatureUtils.normalizePath("//inner///job/%2e/task/../run/?trace=1"));
    }

    @Test
    void normalizePathDoesNotTreatEncodedTraversalAsInnerPath() {
        assertEquals("/admin",
                InternalSignatureUtils.normalizePath("/inner/%2e%2e/admin"));
    }

    @Test
    void normalizeRequestPathStripsContextPathAfterCanonicalization() {
        assertEquals("/inner/report",
                InternalSignatureUtils.normalizeRequestPath("/api//inner/%2E/task/../report/", "/api"));
    }

    @Test
    void normalizeRequestPathDecodesUnreservedCharactersButKeepsEncodedSlash() {
        assertEquals("/inner/report",
                InternalSignatureUtils.normalizeRequestPath("/api/%69nner/report", "/api"));
        assertEquals("/%2Finner/report",
                InternalSignatureUtils.normalizeRequestPath("/api/%2finner/report", "/api"));
    }

    @Test
    void normalizeQuerySortsNamesButPreservesDuplicateValueOrder() {
        assertEquals(
                "a=first&a=second&b=%2F&flag&name=alice",
                InternalSignatureUtils.normalizeQuery("b=%2f&a=first&flag&a=second&n%61me=alice"));
        assertNotEquals(
                InternalSignatureUtils.normalizeQuery("a=first&a=second"),
                InternalSignatureUtils.normalizeQuery("a=second&a=first"));
    }

    @Test
    void v2PayloadBindsQueryBodyAndUserContextFields() {
        String baseline = InternalSignatureUtils.userContextPayloadV2(
                "post",
                "/questions",
                "page=1&sort=created",
                "1800000000000",
                "nonce-1234567890",
                "codecoachai-gateway",
                InternalSignatureUtils.EMPTY_BODY_SHA256,
                "42",
                "alice",
                "ADMIN");

        assertNotEquals(
                baseline,
                InternalSignatureUtils.userContextPayloadV2(
                        "post",
                        "/questions",
                        "page=2&sort=created",
                        "1800000000000",
                        "nonce-1234567890",
                        "codecoachai-gateway",
                        InternalSignatureUtils.EMPTY_BODY_SHA256,
                        "42",
                        "alice",
                        "ADMIN"));
        assertNotEquals(
                baseline,
                InternalSignatureUtils.userContextPayloadV2(
                        "post",
                        "/questions",
                        "page=1&sort=created",
                        "1800000000000",
                        "nonce-1234567890",
                        "codecoachai-gateway",
                        InternalSignatureUtils.sha256Hex("changed".getBytes()),
                        "42",
                        "alice",
                        "ADMIN"));
        assertNotEquals(
                baseline,
                InternalSignatureUtils.userContextPayloadV2(
                        "post",
                        "/questions",
                        "page=1&sort=created",
                        "1800000000000",
                        "nonce-1234567890",
                        "codecoachai-gateway",
                        InternalSignatureUtils.EMPTY_BODY_SHA256,
                        "43",
                        "alice",
                        "ADMIN"));
    }
}
