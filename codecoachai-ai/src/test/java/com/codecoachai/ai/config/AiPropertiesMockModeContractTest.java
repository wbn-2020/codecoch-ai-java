package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiPropertiesMockModeContractTest {

    @Test
    void enabledAiRequiresAnExplicitMockModeDecisionBeforeProviderValidation() {
        AiProperties properties = new AiProperties();

        IllegalStateException error = assertThrows(IllegalStateException.class, properties::validate);

        assertTrue(error.getMessage().contains(AiProperties.MOCK_ENABLED_PROPERTY));
    }

    @Test
    void explicitMockModeAllowsOfflineMockOperationWithoutProviderCredentials() {
        AiProperties properties = new AiProperties();
        properties.setMockEnabled(true);

        assertDoesNotThrow(properties::validate);
    }
}
