package com.codecoachai.resume.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codecoachai.common.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class V8FeatureGateTest {

    @Test
    void exportIsClosedByDefaultAndAdvertisedOnlyWhenEnabled() {
        V8FeatureGate gate = new V8FeatureGate();

        assertThrows(BusinessException.class, gate::requireCampaignExport);
        gate.setCampaignExport(true);

        assertEquals(List.of("CAMPAIGN_EXPORT"), gate.enabledCapabilities());
    }
}
