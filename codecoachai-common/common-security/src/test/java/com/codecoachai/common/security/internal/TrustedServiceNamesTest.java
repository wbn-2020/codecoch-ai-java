package com.codecoachai.common.security.internal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TrustedServiceNamesTest {

    @Test
    void consolidatedCoreServiceIsAnAllowedInternalCaller() {
        assertTrue(TrustedServiceNames.contains("codecoachai-core"));
    }

    @Test
    void removedLegacyServiceNamesAreNotAcceptedAsCurrentCallers() {
        assertFalse(TrustedServiceNames.contains("codecoachai-auth"));
        assertFalse(TrustedServiceNames.contains("codecoachai-task"));
    }
}
