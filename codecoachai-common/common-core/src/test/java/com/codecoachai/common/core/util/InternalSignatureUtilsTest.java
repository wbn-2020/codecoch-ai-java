package com.codecoachai.common.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void normalizeRequestPathKeepsEncodedInnerPrefixEncoded() {
        assertEquals("/%69nner/report",
                InternalSignatureUtils.normalizeRequestPath("/api/%69nner/report", "/api"));
        assertEquals("/%2Finner/report",
                InternalSignatureUtils.normalizeRequestPath("/api/%2finner/report", "/api"));
    }
}
