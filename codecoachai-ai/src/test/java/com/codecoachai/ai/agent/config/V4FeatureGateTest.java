package com.codecoachai.ai.agent.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.exception.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

class V4FeatureGateTest {

    @Test
    void adaptivePlanEnvironmentSwitchIsExplicitAndDefaultsToClosed() throws IOException {
        ClassPathResource resource = new ClassPathResource("application.yml");
        String yaml;
        try (var input = resource.getInputStream()) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(yaml.contains(
                "adaptive-plan-enabled: ${CODECOACHAI_V4_ADAPTIVE_PLAN_ENABLED:false}"));
    }

    @Test
    void adaptivePlanGateRejectsByDefaultAndAllowsExplicitEnablement() {
        V4FeatureGate gate = new V4FeatureGate();

        assertThrows(BusinessException.class, gate::requireAdaptivePlanEnabled);

        ReflectionTestUtils.setField(gate, "adaptivePlanEnabled", true);
        assertTrue(gate.isAdaptivePlanEnabled());
        assertDoesNotThrow(gate::requireAdaptivePlanEnabled);
    }
}
