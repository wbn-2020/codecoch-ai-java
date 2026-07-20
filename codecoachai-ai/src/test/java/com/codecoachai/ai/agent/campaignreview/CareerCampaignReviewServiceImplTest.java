package com.codecoachai.ai.agent.campaignreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReview;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSnapshot;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewMemoryCandidateMapper;
import com.codecoachai.ai.agent.feign.CareerCampaignReviewEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.CareerCampaignReviewEvidenceVO;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewAiService;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewPersistenceService;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewServiceImpl;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerCampaignReviewServiceImplTest {

    @Mock
    private CareerCampaignReviewAiService aiService;
    @Mock
    private CareerCampaignReviewPersistenceService persistenceService;
    @Mock
    private CareerCampaignReviewMemoryCandidateMapper candidateMapper;
    @Mock
    private CareerCampaignReviewEvidenceFeignClient evidenceClient;

    @Test
    void activeCampaignIsBlockedBeforePersistence() {
        CareerCampaignReviewGenerateDTO request = request("ACTIVE");
        when(evidenceClient.get(9L, 20L, request.getDataCutoffAt()))
                .thenReturn(Result.success(evidence("ACTIVE")));
        CareerCampaignReviewServiceImpl service = service();
        assertThrows(BusinessException.class, () -> service.generate(9L, request));
        verifyNoInteractions(persistenceService, aiService, candidateMapper);
    }

    @Test
    void idempotentSnapshotIsReadBackWithoutNewClaim() {
        CareerCampaignReviewGenerateDTO request = request("COMPLETED");
        when(evidenceClient.get(9L, 20L, request.getDataCutoffAt()))
                .thenReturn(Result.success(evidence("COMPLETED")));
        CareerCampaignReview review = new CareerCampaignReview();
        review.setId(10L);
        review.setCampaignId(20L);
        CareerCampaignReviewSnapshot snapshot = new CareerCampaignReviewSnapshot();
        snapshot.setId(30L);
        snapshot.setSnapshotVersion(2);
        review.setReviewStatus("READY");
        snapshot.setFactsJson("[]");
        snapshot.setCoverageJson("[]");
        snapshot.setLimitsJson("[]");
        snapshot.setSignalsJson("[]");
        snapshot.setMemoryCandidatesJson("[]");
        snapshot.setExperimentCandidatesJson("[]");
        snapshot.setNextCycleActionsJson("[]");
        when(persistenceService.findIdempotentReplay(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new CareerCampaignReviewPersistenceService.Replay(review, snapshot));
        when(candidateMapper.selectBySnapshot(9L, 30L)).thenReturn(List.of());

        var result = service().generate(9L, request);
        assertEquals(10L, result.getReviewId());
        assertEquals(30L, result.getSnapshotId());
        assertEquals(2, result.getSnapshotVersion());
    }

    @Test
    void blocksGenerationWhenResumeEvidenceIsUnavailable() {
        assertThrows(BusinessException.class, () -> service().generate(9L, request("COMPLETED")));
        verifyNoInteractions(persistenceService, aiService, candidateMapper);
    }

    private CareerCampaignReviewServiceImpl service() {
        return new CareerCampaignReviewServiceImpl(
                aiService, persistenceService, candidateMapper,
                new ObjectMapper().findAndRegisterModules(), evidenceClient);
    }

    private CareerCampaignReviewEvidenceVO evidence(String status) {
        CareerCampaignReviewEvidenceVO evidence = new CareerCampaignReviewEvidenceVO();
        evidence.setUserId(9L);
        evidence.setCampaignId(20L);
        evidence.setCampaignStatus(status);
        evidence.setCompleted("COMPLETED".equals(status));
        evidence.setAllOpportunitiesClosed(true);
        return evidence;
    }

    private CareerCampaignReviewGenerateDTO request(String status) {
        CareerCampaignReviewGenerateDTO request = new CareerCampaignReviewGenerateDTO();
        request.setCampaignId(20L);
        request.setCampaignStatus(status);
        request.setIdempotencyKey("campaign-review-key");
        request.setCompleted("COMPLETED".equals(status));
        request.setAllOpportunitiesClosed(true);
        request.setDataCutoffAt(LocalDateTime.of(2026, 7, 20, 12, 0));
        return request;
    }
}
