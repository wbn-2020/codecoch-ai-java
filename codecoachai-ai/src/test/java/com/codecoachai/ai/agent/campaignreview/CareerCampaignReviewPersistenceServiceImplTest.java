package com.codecoachai.ai.agent.campaignreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReview;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewMemoryCandidate;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSnapshot;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewMapper;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewMemoryCandidateMapper;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewSnapshotMapper;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewSourceMapper;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewPersistenceServiceImpl;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerCampaignReviewPersistenceServiceImplTest {

    @Mock
    private CareerCampaignReviewMapper reviewMapper;
    @Mock
    private CareerCampaignReviewSnapshotMapper snapshotMapper;
    @Mock
    private CareerCampaignReviewSourceMapper sourceMapper;
    @Mock
    private CareerCampaignReviewMemoryCandidateMapper candidateMapper;

    @Test
    void claimUsesLockedRootVersionInsteadOfMaxSnapshotQuery() {
        CareerCampaignReview root = new CareerCampaignReview();
        root.setId(3L);
        root.setCampaignId(7L);
        root.setSnapshotVersion(4);
        when(reviewMapper.selectIdentityForUpdate(9L, 7L)).thenReturn(root);
        when(snapshotMapper.selectByIdempotency(9L, 3L, "key")).thenReturn(null);
        when(reviewMapper.claimGeneration(eq(9L), eq(3L), eq("fingerprint"),
                anyString(), eq("key"), eq("payload"), any(), any())).thenReturn(1);

        var claim = service().claimGeneration(9L, 7L, "fingerprint", "key", "payload");
        assertTrue(claim.owner());
        assertEquals(5, claim.nextSnapshotVersion());
    }

    @Test
    void confirmedCandidateChangesStateOnlyAfterDecision() {
        CareerCampaignReviewMemoryCandidate candidate = new CareerCampaignReviewMemoryCandidate();
        candidate.setId(11L);
        candidate.setStatus("PENDING");
        when(candidateMapper.selectOwnedForUpdate(9L, 11L)).thenReturn(candidate);
        when(candidateMapper.decide(eq(9L), eq(11L), eq("CONFIRMED"),
                eq("confirm-key"), any())).thenReturn(1);

        var result = service().confirmCandidate(9L, 11L, "confirm-key", true);
        assertEquals("CONFIRMED", result.getStatus());
    }

    @Test
    void rejectsConflictingDecisionAfterCandidateWasAlreadyDecided() {
        CareerCampaignReviewMemoryCandidate candidate = new CareerCampaignReviewMemoryCandidate();
        candidate.setId(11L);
        candidate.setStatus("CONFIRMED");
        when(candidateMapper.selectOwnedForUpdate(9L, 11L)).thenReturn(candidate);

        assertThrows(BusinessException.class,
                () -> service().confirmCandidate(9L, 11L, "reject-key", false));
    }

    @Test
    void savesEvidenceEnvelopeMetadataOnImmutableSnapshot() {
        CareerCampaignReview root = new CareerCampaignReview();
        root.setId(3L);
        root.setCampaignId(7L);
        root.setSnapshotVersion(0);
        root.setGenerationClaimToken("claim");
        root.setGenerationClaimFingerprint("fingerprint");
        when(reviewMapper.selectIdentityForUpdate(9L, 7L)).thenReturn(root);
        when(snapshotMapper.insertSnapshot(any())).thenAnswer(invocation -> {
            CareerCampaignReviewSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(21L);
            return 1;
        });
        when(reviewMapper.publishSnapshot(
                9L, 3L, 21L, 1, "COMPLETED", "claim")).thenReturn(1);
        CareerCampaignReviewVO result = new CareerCampaignReviewVO();
        result.setReportStatus("COMPLETED");
        result.setConfidenceLevel("LOW");
        result.setFallback(true);

        service().saveClaimed(
                9L, root, "claim", "key", "payload", result, "input", "request",
                "{\"evidenceHash\":\"abc\"}", "schema-v1", "rule-v1", java.util.List.of());

        ArgumentCaptor<CareerCampaignReviewSnapshot> captor =
                ArgumentCaptor.forClass(CareerCampaignReviewSnapshot.class);
        verify(snapshotMapper).insertSnapshot(captor.capture());
        assertEquals("{\"evidenceHash\":\"abc\"}", captor.getValue().getEvidenceManifestJson());
        assertEquals("schema-v1", captor.getValue().getEvidenceSchemaVersion());
        assertEquals("rule-v1", captor.getValue().getRuleVersion());
    }

    private CareerCampaignReviewPersistenceServiceImpl service() {
        return new CareerCampaignReviewPersistenceServiceImpl(
                reviewMapper, snapshotMapper, sourceMapper, candidateMapper,
                new ObjectMapper().findAndRegisterModules());
    }
}
