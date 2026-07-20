package com.codecoachai.ai.agent.campaignreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewRuleEngine;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewMemoryCandidateService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerCampaignReviewRuleEngineTest {

    private final CareerCampaignReviewRuleEngine engine = new CareerCampaignReviewRuleEngine();

    @Test
    void activeCampaignIsBlocked() {
        CareerCampaignReviewGenerateDTO request = request(false, true, 5);
        assertThrows(IllegalArgumentException.class, () -> engine.aggregate(request));
    }

    @Test
    void lowSampleOnlyEmitsFactsAndProcessSignals() {
        CareerCampaignReviewGenerateDTO request = request(true, true, 1);
        CareerCampaignReviewGenerateDTO.Seed seed = new CareerCampaignReviewGenerateDTO.Seed();
        seed.setSemanticKey("signal-1");
        seed.setTitle("Follow up");
        seed.setDescription("Record follow-up");
        seed.setCausalClaim(true);
        request.setExperimentCandidateSeeds(List.of(seed));

        var result = engine.aggregate(request);
        assertEquals("LOW", result.getConfidenceLevel());
        assertTrue(result.getLimits().contains("LOW_SAMPLE"));
        assertTrue(result.getSignals().get(0).getBlockedConclusions().contains("不能据此认定因果关系"));
    }

    @Test
    void memoryCandidatesAreDeduplicatedAndRequireConfirmation() {
        CareerCampaignReviewGenerateDTO request = request(true, true, 5);
        CareerCampaignReviewGenerateDTO.Seed first = seed("same", "Use a smaller batch");
        CareerCampaignReviewGenerateDTO.Seed second = seed("other-key", "Use a smaller batch");
        request.setMemoryCandidateSeeds(List.of(first, second));
        var review = engine.aggregate(request);
        var service = new CareerCampaignReviewMemoryCandidateService();
        var candidates = service.candidates(review);
        assertEquals(1, candidates.size());
        assertTrue(!service.effective(candidates.get(0), LocalDateTime.now()));
        service.confirm(candidates.get(0), true);
        assertTrue(service.effective(candidates.get(0), LocalDateTime.now()));
    }

    private CareerCampaignReviewGenerateDTO request(boolean completed, boolean closed, int sampleSize) {
        CareerCampaignReviewGenerateDTO request = new CareerCampaignReviewGenerateDTO();
        request.setCompleted(completed);
        request.setAllOpportunitiesClosed(closed);
        request.setDataCutoffAt(LocalDateTime.of(2026, 7, 20, 12, 0));
        request.setSampleSize(sampleSize);
        return request;
    }

    private CareerCampaignReviewGenerateDTO.Seed seed(String key, String title) {
        CareerCampaignReviewGenerateDTO.Seed seed = new CareerCampaignReviewGenerateDTO.Seed();
        seed.setSemanticKey(key);
        seed.setTitle(title);
        seed.setDescription("Keep the behavior");
        return seed;
    }
}
