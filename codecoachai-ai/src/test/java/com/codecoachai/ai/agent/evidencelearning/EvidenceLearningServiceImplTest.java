package com.codecoachai.ai.agent.evidencelearning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewMemoryCandidate;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewMemoryCandidateMapper;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewPersistenceService;
import com.codecoachai.ai.agent.config.V9FeatureGate;
import com.codecoachai.ai.agent.feign.ResumeEvidenceUsageFactsFeignClient;
import com.codecoachai.ai.agent.feign.ResumeEvidenceUsageFactsVO;
import com.codecoachai.ai.domain.dto.GenerateEvidenceLearningCandidateDTO;
import com.codecoachai.ai.domain.dto.GenerateEvidenceReuseMaterialDraftDTO;
import com.codecoachai.ai.domain.vo.EvidenceLearningCandidateDecisionVO;
import com.codecoachai.ai.domain.vo.EvidenceLearningReuseDraftVO;
import com.codecoachai.ai.domain.vo.EvidenceLearningSourceRefVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceLearningCandidateVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceReuseMaterialDraftVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EvidenceLearningServiceImplTest {

    @Mock
    private ResumeEvidenceUsageFactsFeignClient factsClient;
    @Mock
    private AiService aiService;
    @Mock
    private CareerCampaignReviewMemoryCandidateMapper candidateMapper;
    @Mock
    private CareerCampaignReviewPersistenceService persistenceService;

    private EvidenceLearningServiceImpl service;

    @BeforeEach
    void setUp() {
        V9FeatureGate gate = new V9FeatureGate();
        ReflectionTestUtils.setField(gate, "evidenceLearning", true);
        service = new EvidenceLearningServiceImpl(
                gate, factsClient, aiService, new EvidenceLearningRuleEngine(),
                candidateMapper, persistenceService, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void aiFailureIsReturnedAsBoundedRuleFallback() {
        when(factsClient.getFacts(10L, 70L, null, null, null))
                .thenReturn(Result.success(facts()));
        when(aiService.generateEvidenceLearningCandidate(any(), any()))
                .thenThrow(new IllegalStateException("provider down"));

        var result = service.learningCandidate(10L, request());

        assertTrue(result.getFallback());
        assertTrue(result.getFallbackReason().contains("规则"));
        assertEquals("LOW", result.getConfidenceLevel());
    }

    @Test
    void noUsableSourceReturnsFallbackWithoutCandidateOrStrongDraft() {
        ResumeEvidenceUsageFactsVO facts = new ResumeEvidenceUsageFactsVO();
        facts.setUserId(10L);
        facts.setSourceSetHash("source-set-without-references");
        when(factsClient.getFacts(10L, 70L, null, null, null))
                .thenReturn(Result.success(facts));
        when(aiService.generateEvidenceLearningCandidate(any(), any()))
                .thenThrow(new BusinessException(
                        com.codecoachai.common.core.enums.ErrorCode.SYSTEM_ERROR,
                        "AI 输出缺少可核验来源引用"));

        var result = service.learningCandidate(10L, request());

        assertTrue(result.getFallback());
        assertTrue(result.getCandidateDecision().isEmpty());
        assertTrue(result.getSourceRefs().isEmpty());
    }

    @Test
    void decisionUsesCandidateCommandAndClientKeyForIdempotencyHash() {
        CareerCampaignReviewMemoryCandidate candidate = new CareerCampaignReviewMemoryCandidate();
        candidate.setId(8L);
        candidate.setUserId(10L);
        candidate.setStatus("CONFIRMED_BY_USER");
        candidate.setDecisionCode("KEEP");
        candidate.setTitle("候选");
        candidate.setContent("内容");
        when(persistenceService.decideCandidate(eq(10L), eq(8L), eq("KEEP"),
                any(), any(), eq(null))).thenReturn(candidate);

        EvidenceLearningModels.DecisionCommand command =
                new EvidenceLearningModels.DecisionCommand();
        command.setDecisionCode("KEEP");
        command.setIdempotencyKey("client-key");

        var result = service.decide(10L, 8L, command);

        ArgumentCaptor<String> keyHash = ArgumentCaptor.forClass(String.class);
        verify(persistenceService).decideCandidate(
                eq(10L), eq(8L), eq("KEEP"), keyHash.capture(), any(), eq(null));
        assertTrue(keyHash.getValue().matches("[0-9a-f]{64}"));
        assertEquals("CONFIRMED_BY_USER", result.getStatus());
    }

    @Test
    void dataCutoffIsForwardedAndFutureFactsAreRemovedBeforeAi() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 22, 12, 0);
        ResumeEvidenceUsageFactsVO facts = facts();
        facts.setDataCutoffAt(cutoff.plusDays(1));
        facts.getUsageSnapshots().get(0).setUsedAt(cutoff.minusHours(1));

        ResumeEvidenceUsageFactsVO.UsageFact futureUsage =
                new ResumeEvidenceUsageFactsVO.UsageFact();
        futureUsage.setUsageId(2L);
        futureUsage.setStatus("CAPTURED");
        futureUsage.setUsedAt(cutoff.plusHours(1));
        futureUsage.setSourceHash("FUTURE_FACT_SENTINEL");
        futureUsage.setSourceRefs(List.of("PROJECT_EVIDENCE:999:1"));
        facts.setUsageSnapshots(List.of(facts.getUsageSnapshots().get(0), futureUsage));

        ResumeEvidenceUsageFactsVO.ResultFact pastResult =
                new ResumeEvidenceUsageFactsVO.ResultFact();
        pastResult.setResultId(11L);
        pastResult.setStatus("CONFIRMED");
        pastResult.setOccurredAt(cutoff.minusHours(2));
        pastResult.setConfirmedAt(cutoff.minusMinutes(30));
        pastResult.setKnownFacts(List.of("截止时间前已确认"));

        ResumeEvidenceUsageFactsVO.ResultFact futureResult =
                new ResumeEvidenceUsageFactsVO.ResultFact();
        futureResult.setResultId(12L);
        futureResult.setStatus("CONFIRMED");
        futureResult.setOccurredAt(cutoff.plusMinutes(1));
        futureResult.setConfirmedAt(cutoff.plusMinutes(2));
        futureResult.setKnownFacts(List.of("FUTURE_FACT_SENTINEL"));
        facts.setConfirmedResults(List.of(pastResult, futureResult));

        GenerateEvidenceLearningCandidateDTO request = request();
        request.setDataCutoffAt(cutoff);
        when(factsClient.getFacts(10L, 70L, null, null, cutoff))
                .thenReturn(Result.success(facts));
        when(aiService.generateEvidenceLearningCandidate(any(), any()))
                .thenReturn(new GenerateEvidenceLearningCandidateVO());

        service.learningCandidate(10L, request);

        verify(factsClient).getFacts(10L, 70L, null, null, cutoff);
        ArgumentCaptor<ResumeEvidenceUsageFactsVO> envelope =
                ArgumentCaptor.forClass(ResumeEvidenceUsageFactsVO.class);
        verify(aiService).generateEvidenceLearningCandidate(eq(request), envelope.capture());
        ResumeEvidenceUsageFactsVO forwarded = envelope.getValue();
        assertEquals(cutoff, forwarded.getDataCutoffAt());
        assertEquals(List.of(1L), forwarded.getUsageSnapshots().stream()
                .map(ResumeEvidenceUsageFactsVO.UsageFact::getUsageId)
                .toList());
        assertEquals(List.of(11L), forwarded.getConfirmedResults().stream()
                .map(ResumeEvidenceUsageFactsVO.ResultFact::getResultId)
                .toList());
        assertFalse(forwarded.toString().contains("FUTURE_FACT_SENTINEL"));
    }

    @Test
    void terminalCandidateHistoryDoesNotPreventGeneratingAReplacement() {
        CareerCampaignReviewMemoryCandidate rejected = new CareerCampaignReviewMemoryCandidate();
        rejected.setId(21L);
        rejected.setUserId(10L);
        rejected.setStatus("REJECTED");
        rejected.setCandidateScopeType("CAMPAIGN");
        rejected.setCandidateScopeKey("70");
        when(candidateMapper.selectByScope(10L, "CAMPAIGN", "70", null))
                .thenReturn(List.of(rejected), List.of(rejected));
        when(factsClient.getFacts(10L, 70L, null, null, null))
                .thenReturn(Result.success(facts(5, "replacement-source", 0)));
        when(aiService.generateEvidenceLearningCandidate(any(), any()))
                .thenReturn(new GenerateEvidenceLearningCandidateVO());

        EvidenceLearningModels.CandidateQuery query = new EvidenceLearningModels.CandidateQuery();
        query.setCampaignId(70L);
        service.listCandidates(10L, query);

        verify(aiService).generateEvidenceLearningCandidate(any(), any());
    }

    @Test
    void statusFilteredListDoesNotGenerateAHiddenCandidate() {
        when(candidateMapper.selectByScope(10L, "CAMPAIGN", "70", null))
                .thenReturn(List.of());
        when(candidateMapper.selectByScope(10L, "CAMPAIGN", "70", "REJECTED"))
                .thenReturn(List.of());
        when(factsClient.getFacts(10L, 70L, null, null, null))
                .thenReturn(Result.success(facts()));

        EvidenceLearningModels.CandidateQuery query = new EvidenceLearningModels.CandidateQuery();
        query.setCampaignId(70L);
        query.setStatus("REJECTED");
        service.listCandidates(10L, query);

        verify(aiService, never()).generateEvidenceLearningCandidate(any(), any());
        verify(factsClient).getFacts(10L, 70L, null, null, null);
    }

    @Test
    void continueCandidateExpiresAndRegeneratesWhenFactsVersionChanges() {
        CareerCampaignReviewMemoryCandidate previous =
                candidate(21L, "WEAK_OBSERVATION", "old-source", 5, 1);
        previous.setDecisionCode("CONTINUE");
        CareerCampaignReviewMemoryCandidate replacement =
                candidate(22L, "WEAK_OBSERVATION", "new-source", 6, 2);
        when(candidateMapper.selectByScope(10L, "CAMPAIGN", "70", null))
                .thenReturn(List.of(previous), List.of(previous), List.of(previous, replacement));
        when(candidateMapper.expireForFactsChange(10L, 21L)).thenReturn(1);
        when(factsClient.getFacts(10L, 70L, null, null, null))
                .thenReturn(Result.success(facts(6, "new-source", 2)));
        when(aiService.generateEvidenceLearningCandidate(any(), any()))
                .thenReturn(candidateOutput(999, 999, "HIGH"));
        when(candidateMapper.insertCandidate(any())).thenReturn(1);

        EvidenceLearningModels.CandidateQuery query = new EvidenceLearningModels.CandidateQuery();
        query.setCampaignId(70L);
        var result = service.listCandidates(10L, query);

        verify(candidateMapper).expireForFactsChange(10L, 21L);
        ArgumentCaptor<CareerCampaignReviewMemoryCandidate> inserted =
                ArgumentCaptor.forClass(CareerCampaignReviewMemoryCandidate.class);
        verify(candidateMapper).insertCandidate(inserted.capture());
        assertEquals("new-source", inserted.getValue().getUsageSourceHash());
        assertEquals(6, inserted.getValue().getEvidenceCount());
        assertEquals(2, inserted.getValue().getSampleCount());
        assertEquals("LOW", inserted.getValue().getConfidenceLevel());
        assertTrue(inserted.getValue().getLimitsJson().contains("弱观察"));
        assertEquals("EXPIRED", result.getCandidates().get(0).getStatus());
        assertEquals("new-source", result.getSourceSetHash());
    }

    @Test
    void factsFailureUsesSavedCandidatesForConservativeEnvelope() {
        CareerCampaignReviewMemoryCandidate saved =
                candidate(21L, "WEAK_OBSERVATION", "saved-source", 8, 3);
        saved.setConfidenceLevel("MEDIUM");
        saved.setLimitsJson("[\"保存候选限制\"]");
        when(candidateMapper.selectByScope(10L, "CAMPAIGN", "70", null))
                .thenReturn(List.of(saved));
        when(factsClient.getFacts(10L, 70L, null, null, null))
                .thenThrow(new IllegalStateException("resume unavailable"));

        EvidenceLearningModels.CandidateQuery query = new EvidenceLearningModels.CandidateQuery();
        query.setCampaignId(70L);
        var result = service.listCandidates(10L, query);

        assertTrue(result.getFallback());
        assertEquals("saved-source", result.getSourceSetHash());
        assertEquals("MEDIUM", result.getConfidenceLevel());
        assertTrue(result.getLimits().contains("保存候选限制"));
        assertFalse(result.getLimits().isEmpty());
        verify(candidateMapper, never()).expireForFactsChange(any(), any());
        verify(aiService, never()).generateEvidenceLearningCandidate(any(), any());
    }

    @Test
    void unscopedListReturnsSavedCandidateEnvelopeAndCompleteSourceRef() {
        CareerCampaignReviewMemoryCandidate saved =
                candidate(21L, "WEAK_OBSERVATION", "saved-source", 8, 3);
        saved.setConfidenceLevel("MEDIUM");
        saved.setLimitsJson("[\"保存候选限制\"]");
        saved.setSourceRef(
                "V9|EVIDENCE_USAGE|1|$.usageSnapshots|usage-hash-1");
        saved.setUpdatedAt(LocalDateTime.of(2026, 7, 22, 12, 0));
        when(candidateMapper.selectByScope(10L, null, null, null))
                .thenReturn(List.of(saved));

        var result = service.listCandidates(
                10L, new EvidenceLearningModels.CandidateQuery());

        assertFalse(result.getFallback());
        assertEquals("saved-source", result.getSourceSetHash());
        assertEquals("MEDIUM", result.getConfidenceLevel());
        assertEquals(LocalDateTime.of(2026, 7, 22, 12, 0), result.getDataCutoffAt());
        assertEquals(1, result.getCoverage().get("candidateCount"));
        assertEquals("usage-hash-1", result.getSources().get(0).getSourceHash());
        assertTrue(result.getCandidates().get(0).getAvailableDecisions().contains("CONTINUE"));
        assertTrue(result.getUnknowns().isEmpty());
        verifyNoInteractions(factsClient, aiService);
    }

    @Test
    void lowSampleNormalAiCandidateAndReuseDraftAreCleared() {
        when(factsClient.getFacts(10L, 70L, null, null, null))
                .thenReturn(Result.success(facts()));
        when(aiService.generateEvidenceLearningCandidate(any(), any()))
                .thenReturn(candidateOutput(99, 88, "HIGH"));
        GenerateEvidenceReuseMaterialDraftVO reuse = new GenerateEvidenceReuseMaterialDraftVO();
        EvidenceLearningReuseDraftVO draft = new EvidenceLearningReuseDraftVO();
        draft.setTitle("模型草稿");
        draft.setContent("模型草稿内容");
        reuse.setReuseDraft(draft);
        when(aiService.generateEvidenceReuseMaterialDraft(any(), any())).thenReturn(reuse);

        var candidate = service.learningCandidate(10L, request());
        GenerateEvidenceReuseMaterialDraftDTO reuseRequest =
                new GenerateEvidenceReuseMaterialDraftDTO();
        reuseRequest.setCampaignId(70L);
        var reuseResult = service.reuseMaterialDraft(10L, reuseRequest);

        assertTrue(candidate.getCandidateDecision().isEmpty());
        assertTrue(candidate.getWeakObservations().isEmpty());
        assertNull(reuseResult.getReuseDraft());
        assertTrue(reuseResult.getWeakObservations().isEmpty());
        verify(candidateMapper, never()).insertCandidate(any());
    }

    @Test
    void legacyConfirmedCandidateIsAlreadyConfirmedInV9View() {
        CareerCampaignReviewMemoryCandidate confirmed =
                candidate(21L, "CONFIRMED", "saved-source", 8, 3);
        when(candidateMapper.selectOwned(10L, 21L)).thenReturn(confirmed);

        var result = service.getCandidate(10L, 21L);

        assertFalse(result.getRequiresUserConfirmation());
        assertTrue(result.getAvailableDecisions().isEmpty());
    }

    private GenerateEvidenceLearningCandidateDTO request() {
        GenerateEvidenceLearningCandidateDTO request = new GenerateEvidenceLearningCandidateDTO();
        request.setCampaignId(70L);
        return request;
    }

    private ResumeEvidenceUsageFactsVO facts() {
        return facts(1, "source-set", 0);
    }

    private ResumeEvidenceUsageFactsVO facts(int usageCount, String sourceSetHash, int sampleCount) {
        ResumeEvidenceUsageFactsVO facts = new ResumeEvidenceUsageFactsVO();
        facts.setUserId(10L);
        facts.setSourceSetHash(sourceSetHash);
        java.util.ArrayList<ResumeEvidenceUsageFactsVO.UsageFact> usages =
                new java.util.ArrayList<>();
        for (long index = 1; index <= usageCount; index++) {
            ResumeEvidenceUsageFactsVO.UsageFact usage =
                    new ResumeEvidenceUsageFactsVO.UsageFact();
            usage.setUsageId(index);
            usage.setApplicationId(index);
            usage.setStatus("CAPTURED");
            usage.setSourceHash("usage-hash-" + index);
            usage.setSourceRefs(List.of("PROJECT_EVIDENCE:" + index + ":1"));
            usages.add(usage);
        }
        facts.setUsageSnapshots(usages);
        java.util.ArrayList<ResumeEvidenceUsageFactsVO.ResultFact> results =
                new java.util.ArrayList<>();
        for (long index = 1; index <= sampleCount; index++) {
            ResumeEvidenceUsageFactsVO.ResultFact result =
                    new ResumeEvidenceUsageFactsVO.ResultFact();
            result.setResultId(100L + index);
            result.setUsageId(index);
            result.setStatus("CONFIRMED");
            result.setEventType("INTERVIEW_COMPLETED");
            result.setSourceHash("result-hash-" + index);
            results.add(result);
        }
        facts.setConfirmedResults(results);
        return facts;
    }

    private GenerateEvidenceLearningCandidateVO candidateOutput(
            int usageCount, int sampleCount, String confidence) {
        GenerateEvidenceLearningCandidateVO result = new GenerateEvidenceLearningCandidateVO();
        EvidenceLearningCandidateDecisionVO decision =
                new EvidenceLearningCandidateDecisionVO();
        decision.setCandidateKey("reuse-1");
        decision.setTitle("材料复用候选");
        decision.setContent("保留当前材料使用方式。");
        decision.setUsageCount(usageCount);
        decision.setSampleCount(sampleCount);
        decision.setConfidenceLevel(confidence);
        EvidenceLearningSourceRefVO source = new EvidenceLearningSourceRefVO();
        source.setSourceType("PROJECT_EVIDENCE");
        source.setSourceId("PROJECT_EVIDENCE:1:1");
        source.setFieldPath("$.usageSnapshots[*].sourceRefs");
        source.setSourceHash("usage-hash-1");
        decision.setSourceRefs(List.of(source));
        result.setCandidateDecision(List.of(decision));
        result.setConfidenceLevel(confidence);
        return result;
    }

    private CareerCampaignReviewMemoryCandidate candidate(
            Long id, String status, String sourceHash, int usageCount, int sampleCount) {
        CareerCampaignReviewMemoryCandidate candidate =
                new CareerCampaignReviewMemoryCandidate();
        candidate.setId(id);
        candidate.setUserId(10L);
        candidate.setCandidateScopeType("CAMPAIGN");
        candidate.setCandidateScopeKey("70");
        candidate.setCandidateType("EVIDENCE_REUSE");
        candidate.setCandidateKey("reuse-1");
        candidate.setSemanticHash("semantic-" + id);
        candidate.setTitle("材料复用候选");
        candidate.setContent("保留当前材料使用方式。");
        candidate.setUsageSourceHash(sourceHash);
        candidate.setEvidenceCount(usageCount);
        candidate.setSampleCount(sampleCount);
        candidate.setConfidenceLevel("LOW");
        candidate.setStatus(status);
        candidate.setValidityDays(30);
        return candidate;
    }
}
