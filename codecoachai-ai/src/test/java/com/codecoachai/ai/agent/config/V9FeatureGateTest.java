package com.codecoachai.ai.agent.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codecoachai.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class V9FeatureGateTest {

    @Test
    void evidenceLearningIsClosedByDefault() {
        V9FeatureGate gate = new V9FeatureGate();

        assertThrows(BusinessException.class, gate::requireEvidenceLearning);
    }

    @Test
    void evidenceLearningCanBeEnabledByBoundConfiguration() {
        V9FeatureGate gate = new V9FeatureGate();
        ReflectionTestUtils.setField(gate, "evidenceLearning", true);

        assertDoesNotThrow(gate::requireEvidenceLearning);
    }
}
