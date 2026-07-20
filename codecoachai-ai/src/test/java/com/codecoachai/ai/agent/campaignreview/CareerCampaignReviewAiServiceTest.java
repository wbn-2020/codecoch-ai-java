package com.codecoachai.ai.agent.campaignreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewAiServiceImpl;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewNarrativeEnhancer;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewRuleEngine;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CareerCampaignReviewAiServiceTest {

    @Test
    void enhancerFailureFallsBackToLowRuleResult() {
        CareerCampaignReviewNarrativeEnhancer failing = (request, ruleResult) -> {
            throw new IllegalStateException("timeout");
        };
        CareerCampaignReviewAiServiceImpl service = new CareerCampaignReviewAiServiceImpl(
                new CareerCampaignReviewRuleEngine(), failing);
        CareerCampaignReviewGenerateDTO request = new CareerCampaignReviewGenerateDTO();
        request.setCompleted(true);
        request.setAllOpportunitiesClosed(true);
        request.setDataCutoffAt(LocalDateTime.of(2026, 7, 20, 12, 0));
        request.setSampleSize(10);

        var result = service.generate(request);
        assertTrue(result.getFallback());
        assertEquals("LOW", result.getConfidenceLevel());
        assertEquals("FALLBACK", result.getReportStatus());
    }
}
