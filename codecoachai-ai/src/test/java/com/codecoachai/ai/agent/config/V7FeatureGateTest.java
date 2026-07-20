package com.codecoachai.ai.agent.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codecoachai.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class V7FeatureGateTest {

    @Test
    void externalPlanSourceAndCampaignReviewAreClosedByDefault() {
        V7FeatureGate gate = new V7FeatureGate();

        assertThrows(BusinessException.class, gate::requireExternalPlanSource);
        assertThrows(BusinessException.class, gate::requireCampaignReview);
    }

    @Test
    void explicitlyEnabledCapabilitiesPass() {
        V7FeatureGate gate = new V7FeatureGate();
        ReflectionTestUtils.setField(gate, "externalPlanSource", true);
        ReflectionTestUtils.setField(gate, "campaignReview", true);

        assertDoesNotThrow(gate::requireExternalPlanSource);
        assertDoesNotThrow(gate::requireCampaignReview);
    }
}
