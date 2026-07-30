package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.feign.ResumeEvidenceUsageFactsFeignClient;
import com.codecoachai.ai.agent.feign.ResumeEvidenceUsageFactsVO;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.GenerateEvidenceLearningCandidateDTO;
import com.codecoachai.ai.domain.dto.GenerateEvidenceReuseMaterialDraftDTO;
import com.codecoachai.ai.domain.dto.GenerateEvidenceUsageResultDraftDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiServiceImplV9EvidenceSceneTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private PromptRenderService promptRenderService;
    @Mock
    private AiCallLogService aiCallLogService;
    @Mock
    private ResumeEvidenceUsageFactsFeignClient factsClient;

    private AiProperties properties;
    private AiServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setMockEnabled(false);
        properties.setModel("deepseek-chat");
        when(promptRenderService.render(anyString(), anyString(), anyMap()))
                .thenAnswer(invocation -> PromptRenderResult.builder()
                        .scene(invocation.getArgument(0))
                        .renderedPrompt("rendered-prompt")
                        .promptVersion("v9-1")
                        .inputVariablesJson("{}")
                        .promptHash("prompt-hash")
                        .build());
        service = new AiServiceImpl(
                aiCallLogMapper, promptRenderService, aiCallLogService,
                properties, new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        ReflectionTestUtils.setField(service, "resumeEvidenceUsageFactsFeignClient", factsClient);
        lenient().when(factsClient.getFacts(10L, 70L, null, null, null))
                .thenReturn(Result.success(facts()));
    }

    @Test
    void allScenesRenderCompleteSchemaAndParseJsonOrCodeFence() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenReturn(route(resultJson()))
                .thenReturn(route("```json\n" + candidateJson() + "\n```"))
                .thenReturn(route(reuseJson()));

        var result = service.generateEvidenceUsageResultDraft(resultRequest());
        var candidate = service.generateEvidenceLearningCandidate(candidateRequest());
        var reuse = service.generateEvidenceReuseMaterialDraft(reuseRequest());

        assertEquals("EVIDENCE_USAGE_RESULT_DRAFT_V9", result.getScene());
        assertEquals("EVIDENCE_LEARNING_CANDIDATE_V9", candidate.getScene());
        assertEquals("EVIDENCE_REUSE_MATERIAL_DRAFT_V9", reuse.getScene());
        assertFalse(result.getFallback());
        assertFalse(candidate.getFallback());
        assertFalse(reuse.getFallback());
        assertEquals("PROJECT_EVIDENCE:123:2", candidate.getSourceRefs().get(0).getSourceId());
        assertNotNull(reuse.getReuseDraft());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(promptRenderService, times(3)).render(anyString(), prompt.capture(), anyMap());
        for (String value : prompt.getAllValues()) {
            assertTrue(value.contains("\"summary\""));
            assertTrue(value.contains("\"facts\""));
            assertTrue(value.contains("\"weakObservations\""));
            assertTrue(value.contains("\"unknowns\""));
            assertTrue(value.contains("\"limits\""));
            assertTrue(value.contains("\"candidateDecision\""));
            assertTrue(value.contains("\"reuseDraft\""));
            assertTrue(value.contains("\"sourceRefs\""));
            assertTrue(value.contains("\"confidenceLevel\""));
            assertTrue(value.contains("\"fallbackReason\""));
            assertTrue(value.contains("外部反馈、用户解释和 unknowns"));
        }
    }

    @Test
    void mockSceneReturnsChineseSourceBoundOutput() {
        properties.setMockEnabled(true);

        var result = service.generateEvidenceLearningCandidate(candidateRequest());

        assertFalse(result.getFallback());
        assertTrue(result.getSummary().contains("服务端"));
        assertEquals("LOW", result.getConfidenceLevel());
        assertEquals("PROJECT_EVIDENCE:123:2", result.getSourceRefs().get(1).getSourceId());
    }

    @Test
    void realFailureWritesLogAndThrowsBusinessException() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThrows(BusinessException.class,
                () -> service.generateEvidenceUsageResultDraft(resultRequest()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void mismatchedCompleteSourceSignatureIsRejectedAndLogged() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenReturn(route(resultJson().replace(
                        "\"sourceHash\":\"usage-source-hash\"",
                        "\"sourceHash\":\"other-source-hash\"")));

        assertThrows(BusinessException.class,
                () -> service.generateEvidenceUsageResultDraft(resultRequest()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void requestedCutoffFlowsIntoFeignFactsAndRenderedPromptEnvelope() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 22, 12, 0);
        ResumeEvidenceUsageFactsVO cutoffFacts = facts();
        cutoffFacts.setDataCutoffAt(cutoff);
        cutoffFacts.getUsageSnapshots().get(0).setUsedAt(cutoff.minusHours(1));
        when(factsClient.getFacts(10L, 70L, null, null, cutoff))
                .thenReturn(Result.success(cutoffFacts));
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenReturn(route(candidateJson()));
        GenerateEvidenceLearningCandidateDTO request = candidateRequest();
        request.setDataCutoffAt(cutoff);

        var result = service.generateEvidenceLearningCandidate(request);

        verify(factsClient).getFacts(10L, 70L, null, null, cutoff);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(promptRenderService).render(
                eq("EVIDENCE_LEARNING_CANDIDATE_V9"), prompt.capture(), anyMap());
        assertEquals(cutoff, result.getDataCutoffAt());
        assertTrue(prompt.getValue().contains("\"dataCutoffAt\":\"2026-07-22T12:00:00\""));
        assertTrue(prompt.getValue().contains("\"usageId\":1"));
        assertFalse(prompt.getValue().contains("2026-07-24"));
        assertFalse(prompt.getValue().contains("FUTURE_FACT_SENTINEL"));
    }

    @Test
    void resultDraftCannotUpgradeInterviewBoundaryToHighConfidence() {
        when(factsClient.getFacts(10L, 70L, null, null, null))
                .thenReturn(Result.success(mediumConfidenceFacts()));
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenReturn(route(resultJson().replace(
                        "\"confidenceLevel\":\"LOW\"", "\"confidenceLevel\":\"HIGH\"")));

        var result = service.generateEvidenceUsageResultDraft(resultRequest());

        assertEquals("MEDIUM", result.getConfidenceLevel());
        assertTrue(result.getLimits().stream().anyMatch(value -> value.contains("过程趋势")));
    }

    @Test
    void candidateDecisionCannotUpgradeBackendQualityGateToHighConfidence() {
        when(factsClient.getFacts(10L, 70L, null, null, null))
                .thenReturn(Result.success(mediumConfidenceFacts()));
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenReturn(route(candidateJson().replace(
                        "\"confidenceLevel\":\"LOW\"", "\"confidenceLevel\":\"HIGH\"")));

        var result = service.generateEvidenceLearningCandidate(candidateRequest());

        assertEquals("MEDIUM", result.getConfidenceLevel());
        assertEquals("MEDIUM", result.getCandidateDecision().get(0).getConfidenceLevel());
        assertEquals(15, result.getCandidateDecision().get(0).getUsageCount());
        assertEquals(2, result.getCandidateDecision().get(0).getSampleCount());
        assertTrue(result.getCandidateDecision().get(0).getLimits().stream()
                .anyMatch(value -> value.contains("过程趋势")));
    }

    @Test
    void lowSampleCandidateOutputIsClearedByBackendQualityGate() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenReturn(route(candidateJson()));

        var result = service.generateEvidenceLearningCandidate(candidateRequest());

        assertEquals("LOW", result.getConfidenceLevel());
        assertTrue(result.getCandidateDecision().isEmpty());
        assertTrue(result.getWeakObservations().isEmpty());
    }

    private GenerateEvidenceUsageResultDraftDTO resultRequest() {
        GenerateEvidenceUsageResultDraftDTO request = new GenerateEvidenceUsageResultDraftDTO();
        request.setUserId(10L);
        request.setCampaignId(70L);
        return request;
    }

    private GenerateEvidenceLearningCandidateDTO candidateRequest() {
        GenerateEvidenceLearningCandidateDTO request = new GenerateEvidenceLearningCandidateDTO();
        request.setUserId(10L);
        request.setCampaignId(70L);
        return request;
    }

    private GenerateEvidenceReuseMaterialDraftDTO reuseRequest() {
        GenerateEvidenceReuseMaterialDraftDTO request = new GenerateEvidenceReuseMaterialDraftDTO();
        request.setUserId(10L);
        request.setCampaignId(70L);
        return request;
    }

    private ResumeEvidenceUsageFactsVO facts() {
        ResumeEvidenceUsageFactsVO facts = new ResumeEvidenceUsageFactsVO();
        facts.setUserId(10L);
        facts.setSourceSetHash("server-source-set");
        ResumeEvidenceUsageFactsVO.UsageFact usage = new ResumeEvidenceUsageFactsVO.UsageFact();
        usage.setUsageId(1L);
        usage.setApplicationId(11L);
        usage.setStatus("CAPTURED");
        usage.setSourceHash("usage-source-hash");
        usage.setSourceRefs(List.of("PROJECT_EVIDENCE:123:2"));
        facts.setUsageSnapshots(List.of(usage));
        return facts;
    }

    private ResumeEvidenceUsageFactsVO mediumConfidenceFacts() {
        ResumeEvidenceUsageFactsVO facts = facts();
        List<ResumeEvidenceUsageFactsVO.UsageFact> usages = new ArrayList<>();
        for (long index = 1; index <= 15; index++) {
            ResumeEvidenceUsageFactsVO.UsageFact usage = new ResumeEvidenceUsageFactsVO.UsageFact();
            usage.setUsageId(index);
            usage.setApplicationId(100L + index);
            usage.setStatus("CAPTURED");
            usage.setSourceHash(index == 1 ? "usage-source-hash" : "usage-source-hash-" + index);
            usage.setSourceRefs(List.of("PROJECT_EVIDENCE:123:2"));
            usages.add(usage);
        }
        facts.setUsageSnapshots(usages);
        List<ResumeEvidenceUsageFactsVO.ResultFact> results = new ArrayList<>();
        for (long index = 1; index <= 2; index++) {
            ResumeEvidenceUsageFactsVO.ResultFact result = new ResumeEvidenceUsageFactsVO.ResultFact();
            result.setResultId(200L + index);
            result.setUsageId(index);
            result.setEventType("INTERVIEW_ROUND");
            result.setStatus("CONFIRMED");
            results.add(result);
        }
        facts.setConfirmedResults(results);
        return facts;
    }

    private RouteResult route(String content) {
        RouteResult result = new RouteResult();
        result.setContent(content);
        result.setAiCallLogId(901L);
        return result;
    }

    private String resultJson() {
        return """
                {"summary":"已回读服务端事实。","facts":["存在 1 条使用记录。"],
                 "weakObservations":["样本较少，仅作观察。"],"unknowns":["尚无确认结果。"],
                 "limits":["不能做因果判断。"],"candidateDecision":[],"reuseDraft":null,
                 "sourceRefs":[{"sourceType":"PROJECT_EVIDENCE","sourceId":"PROJECT_EVIDENCE:123:2",
                 "fieldPath":"$.usageSnapshots[*].sourceRefs","sourceHash":"usage-source-hash"}],
                 "confidenceLevel":"LOW","fallback":false,"fallbackReason":null}
                """;
    }

    private String candidateJson() {
        return """
                {"summary":"生成待确认候选。","facts":["存在 1 条使用记录。"],
                 "weakObservations":["样本较少，仅作观察。"],"unknowns":["尚无确认结果。"],
                 "limits":["不能做因果判断。"],
                 "candidateDecision":[{"candidateKey":"reuse-1","title":"材料复用待确认",
                 "content":"当前材料可作为待确认草稿。","decisionOptions":["KEEP","EDIT","CONTINUE","REJECT"],
                 "usageCount":1,"sampleCount":0,"confidenceLevel":"LOW","limits":["样本较少"],
                 "sourceRefs":[{"sourceType":"PROJECT_EVIDENCE","sourceId":"PROJECT_EVIDENCE:123:2",
                 "fieldPath":"$.usageSnapshots[*].sourceRefs","sourceHash":"usage-source-hash"}],
                 "requiresUserConfirmation":true}],"reuseDraft":null,
                 "sourceRefs":[{"sourceType":"PROJECT_EVIDENCE","sourceId":"PROJECT_EVIDENCE:123:2",
                 "fieldPath":"$.usageSnapshots[*].sourceRefs","sourceHash":"usage-source-hash"}],
                 "confidenceLevel":"LOW","fallback":false,"fallbackReason":null}
                """;
    }

    private String reuseJson() {
        return """
                {"summary":"生成复用材料草稿。","facts":["存在 1 条使用记录。"],
                 "weakObservations":["样本较少，仅作观察。"],"unknowns":["尚无确认结果。"],
                 "limits":["不能做因果判断。"],"candidateDecision":[],
                 "reuseDraft":{"title":"材料草稿","content":"请结合新场景补充事实后使用。",
                 "editDeepLink":"/evidence-assets","requiresUserConfirmation":true},
                 "sourceRefs":[{"sourceType":"PROJECT_EVIDENCE","sourceId":"PROJECT_EVIDENCE:123:2",
                 "fieldPath":"$.usageSnapshots[*].sourceRefs","sourceHash":"usage-source-hash"}],
                 "confidenceLevel":"LOW","fallback":false,"fallbackReason":null}
                """;
    }
}
