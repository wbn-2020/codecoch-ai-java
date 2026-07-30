package com.codecoachai.ai.agent.campaignreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.feign.CareerCampaignReviewEvidenceVO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerCampaignReviewEvidenceEnvelopeTest {

    @Test
    void trustedRequestContainsOnlyServerEvidenceAndIdentityFields() {
        CareerCampaignReviewEvidenceVO evidence = evidence();
        CareerCampaignReviewEvidenceEnvelope envelope =
                CareerCampaignReviewEvidenceEnvelope.from(
                        evidence, LocalDateTime.of(2026, 7, 21, 10, 0));
        CareerCampaignReviewGenerateDTO publicRequest = new CareerCampaignReviewGenerateDTO();
        publicRequest.setCampaignId(20L);
        publicRequest.setIdempotencyKey("key");
        publicRequest.setRequestId("request-id");
        publicRequest.setCampaignStatus("ACTIVE");
        publicRequest.setCompleted(false);
        publicRequest.setDataCutoffAt(LocalDateTime.of(1999, 1, 1, 0, 0));
        publicRequest.setFacts(List.of(fakeFact()));
        publicRequest.setMemoryCandidateSeeds(List.of(fakeSeed()));
        publicRequest.setExperimentCandidateSeeds(List.of(fakeSeed()));
        publicRequest.setNextCycleActionSeeds(List.of(fakeSeed()));

        CareerCampaignReviewGenerateDTO trusted = envelope.trustedRequest(publicRequest);

        assertEquals("COMPLETED", trusted.getCampaignStatus());
        assertEquals(Boolean.TRUE, trusted.getCompleted());
        assertEquals(evidence.getDataCutoffAt(), trusted.getDataCutoffAt());
        assertEquals("server.fact", trusted.getFacts().get(0).getKey());
        assertTrue(trusted.getMemoryCandidateSeeds().isEmpty());
        assertTrue(trusted.getExperimentCandidateSeeds().isEmpty());
        assertTrue(trusted.getNextCycleActionSeeds().isEmpty());
        assertEquals(64, envelope.getEvidenceHash().length());
        assertEquals(64, envelope.getInputHash().length());
        assertTrue(envelope.sourceMetadataJson(1)
                .contains(CareerCampaignReviewVersions.RULE_VERSION));
    }

    @Test
    void payloadHashDoesNotDependOnPublicFactsOrSeeds() {
        CareerCampaignReviewEvidenceEnvelope envelope =
                CareerCampaignReviewEvidenceEnvelope.from(
                        evidence(), LocalDateTime.of(2026, 7, 21, 10, 0));
        CareerCampaignReviewGenerateDTO first = new CareerCampaignReviewGenerateDTO();
        first.setCampaignId(20L);
        first.setIdempotencyKey("key");
        CareerCampaignReviewGenerateDTO second = new CareerCampaignReviewGenerateDTO();
        second.setCampaignId(20L);
        second.setIdempotencyKey("key");
        second.setFacts(List.of(fakeFact()));
        second.setMemoryCandidateSeeds(List.of(fakeSeed()));
        second.setDataCutoffAt(LocalDateTime.of(2099, 1, 1, 0, 0));

        assertEquals(envelope.payloadHash(), envelope.payloadHash());
        assertFalse(envelope.generationFingerprint().isEmpty());
        assertEquals(
                envelope.trustedRequest(first).getDataCutoffAt(),
                envelope.trustedRequest(second).getDataCutoffAt());
    }

    private CareerCampaignReviewEvidenceVO evidence() {
        CareerCampaignReviewEvidenceVO evidence = new CareerCampaignReviewEvidenceVO();
        evidence.setUserId(9L);
        evidence.setCampaignId(20L);
        evidence.setCampaignStatus("COMPLETED");
        evidence.setCampaignTitle("Server campaign");
        evidence.setCompleted(true);
        evidence.setAllOpportunitiesClosed(true);
        evidence.setSampleSize(1);
        evidence.setDataCutoffAt(LocalDateTime.of(2026, 7, 20, 12, 0));
        CareerCampaignReviewEvidenceVO.Fact fact =
                new CareerCampaignReviewEvidenceVO.Fact();
        fact.setKey("server.fact");
        fact.setValue(1);
        evidence.setFacts(List.of(fact));
        CareerCampaignReviewEvidenceVO.Source source =
                new CareerCampaignReviewEvidenceVO.Source();
        source.setSourceType("CAREER_CAMPAIGN");
        source.setSourceId(20L);
        source.setSourceVersion(1);
        source.setSourceHash("server-hash");
        evidence.setSources(List.of(source));
        return evidence;
    }

    private CareerCampaignReviewGenerateDTO.Fact fakeFact() {
        CareerCampaignReviewGenerateDTO.Fact fact =
                new CareerCampaignReviewGenerateDTO.Fact();
        fact.setKey("client.fact");
        return fact;
    }

    private CareerCampaignReviewGenerateDTO.Seed fakeSeed() {
        CareerCampaignReviewGenerateDTO.Seed seed =
                new CareerCampaignReviewGenerateDTO.Seed();
        seed.setSemanticKey("client.seed");
        return seed;
    }
}
