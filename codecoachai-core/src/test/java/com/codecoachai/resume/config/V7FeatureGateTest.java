package com.codecoachai.resume.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codecoachai.common.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class V7FeatureGateTest {

    @Test
    void disabledCapabilityReturnsExplicitBusinessError() {
        V7FeatureGate gate = new V7FeatureGate();

        assertThrows(BusinessException.class, gate::requireCampaignWorkspace);
        assertThrows(BusinessException.class, gate::requireRealInterview);
        assertThrows(BusinessException.class, gate::requireOffer);
        assertThrows(BusinessException.class, gate::requireContactActivity);
        assertThrows(BusinessException.class, gate::requireResearch);
    }

    @Test
    void enabledCapabilitiesOnlyAdvertiseOpenOptionalDomains() {
        V7FeatureGate gate = new V7FeatureGate();
        ReflectionTestUtils.setField(gate, "realInterview", true);
        ReflectionTestUtils.setField(gate, "offer", false);
        ReflectionTestUtils.setField(gate, "contactActivity", true);
        ReflectionTestUtils.setField(gate, "research", true);

        assertEquals(List.of("REAL_INTERVIEW", "CONTACT_ACTIVITY", "RESEARCH"),
                gate.enabledCapabilities());
    }
}
