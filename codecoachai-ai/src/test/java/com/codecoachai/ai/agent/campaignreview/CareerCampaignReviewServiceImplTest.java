package com.codecoachai.ai.agent.campaignreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReview;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSnapshot;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;
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
import org.mockito.ArgumentCaptor;
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
        when(evidenceClient.get(eq(9L), eq(20L), any(LocalDateTime.class)))
                .thenReturn(Result.success(evidence("ACTIVE")));
        CareerCampaignReviewServiceImpl service = service();
        assertThrows(BusinessException.class, () -> service.generate(9L, request));
        verifyNoInteractions(persistenceService, aiService, candidateMapper);
    }

    @Test
    void idempotentSnapshotIsReadBackWithoutNewClaim() {
        CareerCampaignReviewGenerateDTO request = request("COMPLETED");
        when(evidenceClient.get(eq(9L), eq(20L), any(LocalDateTime.class)))
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
    void publicEvidenceFieldsAreIgnoredInFavorOfServerEnvelope() {
        CareerCampaignReviewGenerateDTO request = request("COMPLETED");
        request.setCompleted(false);
        request.setAllOpportunitiesClosed(false);
        request.setSampleSize(999);
        request.setDataCutoffAt(LocalDateTime.of(2099, 1, 1, 0, 0));
        CareerCampaignReviewGenerateDTO.Fact fakeFact =
                new CareerCampaignReviewGenerateDTO.Fact();
        fakeFact.setKey("fake.client.fact");
        request.setFacts(List.of(fakeFact));
        CareerCampaignReviewGenerateDTO.Seed fakeSeed =
                new CareerCampaignReviewGenerateDTO.Seed();
        fakeSeed.setSemanticKey("fake.client.seed");
        request.setMemoryCandidateSeeds(List.of(fakeSeed));

        CareerCampaignReviewEvidenceVO serverEvidence = evidence("COMPLETED");
        LocalDateTime trustedCutoff = LocalDateTime.of(2026, 7, 20, 12, 0);
        serverEvidence.setDataCutoffAt(trustedCutoff);
        serverEvidence.setSampleSize(1);
        CareerCampaignReviewEvidenceVO.Fact trustedFact =
                new CareerCampaignReviewEvidenceVO.Fact();
        trustedFact.setKey("application.count");
        trustedFact.setValue(1);
        trustedFact.setSourceRef("CAREER_CAMPAIGN:20");
        serverEvidence.setFacts(List.of(trustedFact));
        when(evidenceClient.get(eq(9L), eq(20L), any(LocalDateTime.class)))
                .thenReturn(Result.success(serverEvidence));

        CareerCampaignReview review = new CareerCampaignReview();
        review.setId(10L);
        review.setCampaignId(20L);
        CareerCampaignReviewSnapshot snapshot = emptySnapshot(trustedCutoff);
        when(persistenceService.findIdempotentReplay(
                eq(9L), eq(20L), anyString(), anyString())).thenReturn(null);
        when(persistenceService.claimGeneration(
                eq(9L), eq(20L), anyString(), anyString(), anyString()))
                .thenReturn(new CareerCampaignReviewPersistenceService.GenerationClaim(
                        review, null, "claim-token", 1, true));
        when(aiService.generate(any())).thenReturn(new CareerCampaignReviewVO());
        when(persistenceService.saveClaimed(
                anyLong(), any(), anyString(), anyString(), anyString(), any(),
                anyString(), nullable(String.class), anyString(), anyString(),
                anyString(), anyList())).thenReturn(snapshot);
        when(candidateMapper.selectBySnapshot(9L, snapshot.getId())).thenReturn(List.of());

        service().generate(9L, request);

        ArgumentCaptor<CareerCampaignReviewGenerateDTO> captured =
                ArgumentCaptor.forClass(CareerCampaignReviewGenerateDTO.class);
        verify(aiService).generate(captured.capture());
        CareerCampaignReviewGenerateDTO trusted = captured.getValue();
        assertEquals("COMPLETED", trusted.getCampaignStatus());
        assertEquals(Boolean.TRUE, trusted.getCompleted());
        assertEquals(Boolean.TRUE, trusted.getAllOpportunitiesClosed());
        assertEquals(1, trusted.getSampleSize());
        assertEquals(trustedCutoff, trusted.getDataCutoffAt());
        assertEquals("application.count", trusted.getFacts().get(0).getKey());
        assertFalse(trusted.getFacts().stream()
                .anyMatch(fact -> "fake.client.fact".equals(fact.getKey())));
        assertTrue(trusted.getMemoryCandidateSeeds().isEmpty());
        assertTrue(trusted.getExperimentCandidateSeeds().isEmpty());
        assertTrue(trusted.getNextCycleActionSeeds().isEmpty());

        ArgumentCaptor<LocalDateTime> requestedCutoff =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(evidenceClient).get(eq(9L), eq(20L), requestedCutoff.capture());
        assertFalse(request.getDataCutoffAt().equals(requestedCutoff.getValue()));
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
        evidence.setDataCutoffAt(LocalDateTime.of(2026, 7, 20, 12, 0));
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

    private CareerCampaignReviewSnapshot emptySnapshot(LocalDateTime dataCutoffAt) {
        CareerCampaignReviewSnapshot snapshot = new CareerCampaignReviewSnapshot();
        snapshot.setId(30L);
        snapshot.setSnapshotVersion(1);
        snapshot.setDataCutoffAt(dataCutoffAt);
        snapshot.setFactsJson("[]");
        snapshot.setCoverageJson("[]");
        snapshot.setLimitsJson("[]");
        snapshot.setSignalsJson("[]");
        snapshot.setMemoryCandidatesJson("[]");
        snapshot.setExperimentCandidatesJson("[]");
        snapshot.setNextCycleActionsJson("[]");
        return snapshot;
    }
}
