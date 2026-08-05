package com.codecoachai.common.security.internal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrustedServiceNamesTest {

    @Test
    void consolidatedCoreServiceIsAnAllowedInternalCaller() {
        assertTrue(TrustedServiceNames.contains("codecoachai-core"));
    }
}
