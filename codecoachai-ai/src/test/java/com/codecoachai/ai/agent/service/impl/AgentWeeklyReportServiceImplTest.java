package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportGenerateDTO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportQueryDTO;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReport;
import com.codecoachai.ai.agent.domain.vo.weekly.AgentWeeklyReportVO;
import com.codecoachai.ai.agent.weekly.config.WeeklyReportFeatureProperties;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.AggregationResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.EvidenceBundle;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.GenerationClaim;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.NarrativeResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.QueryContext;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.RequestContext;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.SaveCommand;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.StoredView;
import com.codecoachai.ai.agent.weekly.service.AgentWeeklyReportPersistenceService;
import com.codecoachai.ai.agent.weekly.service.WeeklyReportAiEnhancer;
import com.codecoachai.ai.agent.weekly.service.WeeklyReportEvidenceCollector;
import com.codecoachai.ai.agent.weekly.service.WeeklyReportRequestValidator;
import com.codecoachai.ai.agent.weekly.service.WeeklyReportRuleEngine;
import com.codecoachai.ai.agent.weekly.service.WeeklyReportViewAssembler;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentWeeklyReportServiceImplTest {

    @Mock
    private WeeklyReportFeatureProperties featureProperties;
    @Mock
    private WeeklyReportRequestValidator requestValidator;
    @Mock
    private WeeklyReportEvidenceCollector evidenceCollector;
    @Mock
    private WeeklyReportRuleEngine ruleEngine;
    @Mock
    private WeeklyReportAiEnhancer aiEnhancer;
    @Mock
    private AgentWeeklyReportPersistenceService persistenceService;
    @Mock
    private WeeklyReportViewAssembler viewAssembler;

    @Test
    void currentWithoutSnapshotDoesNotCollectCallAiOrWrite() {
        AgentWeeklyReportQueryDTO query = new AgentWeeklyReportQueryDTO();
        QueryContext context = queryContext();
        AgentWeeklyReportVO expected = new AgentWeeklyReportVO();
        when(requestValidator.query(10L, query, true)).thenReturn(context);
        when(persistenceService.findIdentity(context)).thenReturn(null);
        when(viewAssembler.notGenerated(context, null)).thenReturn(expected);

        AgentWeeklyReportVO result = service().current(10L, query);

        assertSame(expected, result);
        verifyNoInteractions(evidenceCollector, ruleEngine, aiEnhancer);
        verify(persistenceService, never()).saveClaimed(any(), any());
    }

    @Test
    void generateIdempotentReplayReturnsBeforeCollectionAndAi() {
        AgentWeeklyReportGenerateDTO dto = new AgentWeeklyReportGenerateDTO();
        RequestContext context = requestContext();
        StoredView replay = new StoredView();
        AgentWeeklyReportVO expected = new AgentWeeklyReportVO();
        when(requestValidator.generate(10L, dto)).thenReturn(context);
        when(persistenceService.findIdempotentReplay(context)).thenReturn(replay);
        when(viewAssembler.toVO(replay)).thenReturn(expected);

        AgentWeeklyReportVO result = service().generate(10L, dto);

        assertSame(expected, result);
        verifyNoInteractions(evidenceCollector, ruleEngine, aiEnhancer);
        verify(persistenceService, never()).saveClaimed(any(), any());
    }

    @Test
    void generateSameInputReturnsBeforeAiAndSave() {
        AgentWeeklyReportGenerateDTO dto = new AgentWeeklyReportGenerateDTO();
        RequestContext context = requestContext();
        EvidenceBundle evidence = new EvidenceBundle();
        AggregationResult aggregation = aggregation();
        StoredView replay = new StoredView();
        AgentWeeklyReportVO expected = new AgentWeeklyReportVO();
        when(requestValidator.generate(10L, dto)).thenReturn(context);
        when(persistenceService.findIdempotentReplay(context)).thenReturn(null);
        when(evidenceCollector.collect(context)).thenReturn(evidence);
        when(ruleEngine.aggregate(context, evidence)).thenReturn(aggregation);
        when(persistenceService.findGenerationReplay(
                context, aggregation.getGenerationFingerprint(), "REUSED")).thenReturn(replay);
        when(viewAssembler.toVO(replay)).thenReturn(expected);

        AgentWeeklyReportVO result = service().generate(10L, dto);

        assertSame(expected, result);
        verifyNoInteractions(aiEnhancer);
        verify(persistenceService, never()).saveClaimed(any(), any());
    }

    @Test
    void aiFailurePersistsRuleFallbackSnapshot() {
        AgentWeeklyReportGenerateDTO dto = new AgentWeeklyReportGenerateDTO();
        RequestContext context = requestContext();
        EvidenceBundle evidence = new EvidenceBundle();
        AggregationResult aggregation = aggregation();
        StoredView saved = new StoredView();
        AgentWeeklyReportVO expected = new AgentWeeklyReportVO();
        when(requestValidator.generate(10L, dto)).thenReturn(context);
        when(evidenceCollector.collect(context)).thenReturn(evidence);
        when(ruleEngine.aggregate(context, evidence)).thenReturn(aggregation);
        GenerationClaim claim = new GenerationClaim();
        claim.setOwner(true);
        claim.setClaimToken("claim-001");
        when(persistenceService.claimGeneration(
                context, aggregation.getGenerationFingerprint())).thenReturn(claim);
        when(aiEnhancer.enhance(context, aggregation))
                .thenThrow(new IllegalStateException("provider unavailable"));
        when(persistenceService.saveClaimed(any(), eq("claim-001"))).thenReturn(saved);
        when(viewAssembler.toVO(saved)).thenReturn(expected);

        AgentWeeklyReportVO result = service().generate(10L, dto);

        assertSame(expected, result);
        ArgumentCaptor<SaveCommand> commandCaptor = ArgumentCaptor.forClass(SaveCommand.class);
        verify(persistenceService).saveClaimed(commandCaptor.capture(), eq("claim-001"));
        NarrativeResult narrative = commandCaptor.getValue().getNarrative();
        assertTrue(Boolean.TRUE.equals(narrative.getFallback()));
        assertTrue(narrative.getFallbackReason().contains("规则结果"));
        assertSame(aggregation.getHypotheses(), narrative.getHypotheses());
    }

    @Test
    void concurrentClaimReplayDoesNotCallAiOrWriteAnotherSnapshot() {
        AgentWeeklyReportGenerateDTO dto = new AgentWeeklyReportGenerateDTO();
        RequestContext context = requestContext();
        EvidenceBundle evidence = new EvidenceBundle();
        AggregationResult aggregation = aggregation();
        GenerationClaim claim = new GenerationClaim();
        StoredView replay = new StoredView();
        AgentWeeklyReportVO expected = new AgentWeeklyReportVO();
        when(requestValidator.generate(10L, dto)).thenReturn(context);
        when(evidenceCollector.collect(context)).thenReturn(evidence);
        when(ruleEngine.aggregate(context, evidence)).thenReturn(aggregation);
        when(persistenceService.findGenerationReplay(
                context, aggregation.getGenerationFingerprint(), "REUSED"))
                .thenReturn(null);
        when(persistenceService.claimGeneration(
                context, aggregation.getGenerationFingerprint())).thenReturn(claim);
        when(persistenceService.findGenerationReplay(
                context, aggregation.getGenerationFingerprint(), "REPLAY"))
                .thenReturn(replay);
        when(viewAssembler.toVO(replay)).thenReturn(expected);

        AgentWeeklyReportVO result = service().generate(10L, dto);

        assertSame(expected, result);
        verifyNoInteractions(aiEnhancer);
        verify(persistenceService, never()).saveClaimed(any(), any());
    }

    private AgentWeeklyReportServiceImpl service() {
        return new AgentWeeklyReportServiceImpl(
                featureProperties,
                requestValidator,
                evidenceCollector,
                ruleEngine,
                aiEnhancer,
                persistenceService,
                viewAssembler);
    }

    private QueryContext queryContext() {
        QueryContext context = new QueryContext();
        context.setUserId(10L);
        context.setTargetScopeKey("ALL");
        context.setWeekStartDate(LocalDate.of(2026, 7, 13));
        context.setTimezone("Asia/Shanghai");
        context.setZoneId(java.time.ZoneId.of("Asia/Shanghai"));
        return context;
    }

    private RequestContext requestContext() {
        RequestContext context = new RequestContext();
        context.setUserId(10L);
        context.setTargetScopeKey("ALL");
        context.setWeekStartDate(LocalDate.of(2026, 7, 13));
        context.setWeekEndDate(LocalDate.of(2026, 7, 19));
        context.setTimezone("Asia/Shanghai");
        context.setOperation("GENERATE");
        context.setForceRefresh(false);
        return context;
    }

    private AggregationResult aggregation() {
        AggregationResult aggregation = new AggregationResult();
        aggregation.setGenerationFingerprint("generation-001");
        aggregation.setRuleSummary("本周已汇总可核验事实。");
        return aggregation;
    }
}
