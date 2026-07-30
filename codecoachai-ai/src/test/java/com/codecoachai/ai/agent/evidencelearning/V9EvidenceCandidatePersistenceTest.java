package com.codecoachai.ai.agent.evidencelearning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewMemoryCandidate;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewMapper;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewMemoryCandidateMapper;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewSnapshotMapper;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewSourceMapper;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewPersistenceServiceImpl;
import com.codecoachai.ai.agent.domain.entity.AgentMemory;
import com.codecoachai.ai.agent.mapper.AgentMemoryMapper;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class V9EvidenceCandidatePersistenceTest {

    @Mock
    private CareerCampaignReviewMapper reviewMapper;
    @Mock
    private CareerCampaignReviewSnapshotMapper snapshotMapper;
    @Mock
    private CareerCampaignReviewSourceMapper sourceMapper;
    @Mock
    private CareerCampaignReviewMemoryCandidateMapper candidateMapper;
    @Mock
    private AgentMemoryMapper agentMemoryMapper;

    private CareerCampaignReviewPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CareerCampaignReviewPersistenceServiceImpl(
                reviewMapper, snapshotMapper, sourceMapper, candidateMapper,
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(service, "agentMemoryMapper", agentMemoryMapper);
    }

    @Test
    void keepCreatesDisabledUnconfirmedEvidenceLearningMemoryDraft() {
        CareerCampaignReviewMemoryCandidate candidate = candidate("WEAK_OBSERVATION");
        when(candidateMapper.selectOwnedForUpdate(10L, 8L)).thenReturn(candidate);
        when(candidateMapper.decideV9(eq(10L), eq(8L), eq("CONFIRMED_BY_USER"),
                any(), eq("KEEP"), eq("payload"), any(), eq("key"), any(), any(), any()))
                .thenReturn(1);
        when(agentMemoryMapper.selectOne(any())).thenReturn(null);
        when(agentMemoryMapper.insert(any(AgentMemory.class))).thenAnswer(invocation -> {
            AgentMemory memory = invocation.getArgument(0);
            memory.setId(99L);
            return 1;
        });

        var result = service.decideCandidate(10L, 8L, "KEEP", "key", "payload", null);

        assertEquals("CONFIRMED_BY_USER", result.getStatus());
        ArgumentCaptor<AgentMemory> memory = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryMapper).insert(memory.capture());
        assertEquals("EVIDENCE_LEARNING_CANDIDATE", memory.getValue().getSourceType());
        assertEquals(0, memory.getValue().getEnabled());
        verify(candidateMapper).updatePromotedMemory(10L, 8L, 99L);
    }

    @Test
    void continueExtendsObservationWithoutCreatingMemoryOrPlan() {
        CareerCampaignReviewMemoryCandidate candidate = candidate("WEAK_OBSERVATION");
        when(candidateMapper.selectOwnedForUpdate(10L, 8L)).thenReturn(candidate);
        when(candidateMapper.decideV9(eq(10L), eq(8L), eq("WEAK_OBSERVATION"),
                any(), eq("CONTINUE"), eq("payload"), any(), eq("key"), any(), any(), any()))
                .thenReturn(1);

        var result = service.decideCandidate(10L, 8L, "CONTINUE", "key", "payload", null);

        assertEquals("WEAK_OBSERVATION", result.getStatus());
        verify(agentMemoryMapper, never()).insert(any(AgentMemory.class));
    }

    @Test
    void repeatedDecisionWithSamePayloadIsIdempotentButDifferentPayloadConflicts() {
        CareerCampaignReviewMemoryCandidate same = candidate("CONFIRMED_BY_USER");
        same.setDecisionCode("KEEP");
        same.setDecisionIdempotencyKeyHash("key");
        same.setDecisionPayloadHash("payload");
        when(candidateMapper.selectOwnedForUpdate(10L, 8L)).thenReturn(same);

        assertEquals(same, service.decideCandidate(10L, 8L, "KEEP", "key", "payload", null));

        assertThrows(BusinessException.class,
                () -> service.decideCandidate(10L, 8L, "EDIT", "key", "other", "修改内容"));
    }

    @Test
    void decisionHistoryRejectsSameKeyWithDifferentDecisionOrPayload() {
        CareerCampaignReviewMemoryCandidate existing = candidate("WEAK_OBSERVATION");
        existing.setDecisionHistoryJson("""
                {"old-key":{"decisionCode":"CONTINUE","payloadHash":"old-payload",
                "decidedAt":"2026-07-22T12:00:00"}}
                """);
        when(candidateMapper.selectOwnedForUpdate(10L, 8L)).thenReturn(existing);

        assertThrows(BusinessException.class,
                () -> service.decideCandidate(10L, 8L, "KEEP",
                        "old-key", "old-payload", null));

        assertThrows(BusinessException.class,
                () -> service.decideCandidate(10L, 8L, "CONTINUE",
                        "old-key", "different-payload", null));
        verify(candidateMapper, never()).decideV9(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void oldContinueReplayDoesNotRollbackConfirmedRejectedOrExpired() {
        for (String terminalStatus : List.of("CONFIRMED", "REJECTED", "EXPIRED")) {
            CareerCampaignReviewMemoryCandidate existing = candidate(terminalStatus);
            existing.setDecisionCode("KEEP");
            existing.setDecisionPayloadHash("final-payload");
            existing.setDecisionIdempotencyKeyHash("final-key");
            existing.setDecisionHistoryJson("""
                    {"old-key":{"decisionCode":"CONTINUE","payloadHash":"old-payload",
                    "decidedAt":"2026-07-22T12:00:00"}}
                    """);
            when(candidateMapper.selectOwnedForUpdate(10L, 8L)).thenReturn(existing);

            var result = service.decideCandidate(
                    10L, 8L, "CONTINUE", "old-key", "old-payload", null);

            assertEquals(terminalStatus, result.getStatus());
        }

        verify(candidateMapper, never()).decideV9(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(candidateMapper, never()).expire(any(), any(), any());
        verify(agentMemoryMapper, never()).insert(any(AgentMemory.class));
    }

    @Test
    void legacyConfirmedCandidateRejectsANewV9Decision() {
        CareerCampaignReviewMemoryCandidate existing = candidate("CONFIRMED");
        when(candidateMapper.selectOwnedForUpdate(10L, 8L)).thenReturn(existing);

        assertThrows(BusinessException.class,
                () -> service.decideCandidate(
                        10L, 8L, "CONTINUE", "new-key", "new-payload", null));

        verify(candidateMapper, never()).decideV9(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void acceptedDecisionsPersistEveryEntryInDecisionHistoryJson() throws Exception {
        CareerCampaignReviewMemoryCandidate existing = candidate("WEAK_OBSERVATION");
        when(candidateMapper.selectOwnedForUpdate(10L, 8L)).thenReturn(existing);
        when(candidateMapper.decideV9(eq(10L), eq(8L), eq("WEAK_OBSERVATION"),
                any(), eq("CONTINUE"), any(), any(), any(), any(), isNull(), any()))
                .thenReturn(1);

        service.decideCandidate(10L, 8L, "CONTINUE", "key-one", "payload-one", null);
        service.decideCandidate(10L, 8L, "CONTINUE", "key-two", "payload-two", null);

        ArgumentCaptor<String> history = ArgumentCaptor.forClass(String.class);
        verify(candidateMapper, times(2)).decideV9(
                eq(10L), eq(8L), eq("WEAK_OBSERVATION"), any(), eq("CONTINUE"),
                any(), history.capture(), any(), any(), isNull(), any());
        var persisted = new ObjectMapper().readTree(history.getAllValues().get(1));
        assertEquals("CONTINUE", persisted.path("key-one").path("decisionCode").asText());
        assertEquals("payload-one", persisted.path("key-one").path("payloadHash").asText());
        assertEquals("CONTINUE", persisted.path("key-two").path("decisionCode").asText());
        assertEquals("payload-two", persisted.path("key-two").path("payloadHash").asText());
        assertEquals(history.getAllValues().get(1), existing.getDecisionHistoryJson());
    }

    private CareerCampaignReviewMemoryCandidate candidate(String status) {
        CareerCampaignReviewMemoryCandidate candidate = new CareerCampaignReviewMemoryCandidate();
        candidate.setId(8L);
        candidate.setUserId(10L);
        candidate.setSemanticHash("semantic");
        candidate.setStatus(status);
        candidate.setTitle("证据使用候选");
        candidate.setContent("保留当前材料使用方式。");
        candidate.setConfidenceLevel("MEDIUM");
        candidate.setValidityDays(30);
        candidate.setEvidenceCount(5);
        candidate.setSampleCount(2);
        return candidate;
    }
}
