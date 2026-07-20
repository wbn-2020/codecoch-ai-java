package com.codecoachai.ai.agent.weekly.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyExperimentSuggestionVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportFactVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportHypothesisVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportSignalVO;
import com.codecoachai.ai.agent.weekly.config.WeeklyReportFeatureProperties;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.AggregationResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.NarrativeResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.RequestContext;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportVersions;
import com.codecoachai.ai.domain.dto.GenerateAgentWeeklyReportDTO;
import com.codecoachai.ai.domain.vo.GenerateAgentWeeklyReportVO;
import com.codecoachai.ai.service.AiService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyReportAiEnhancerTest {

    @Mock
    private AiService aiService;

    private WeeklyReportFeatureProperties featureProperties;

    @BeforeEach
    void setUp() {
        featureProperties = new WeeklyReportFeatureProperties();
    }

    @Test
    void returnsRuleNarrativeWhenAiIsDisabled() {
        AggregationResult aggregation = aggregation();

        NarrativeResult result = enhancer().enhance(context(), aggregation);

        assertEquals("RULE", result.getResultSource());
        assertEquals(aggregation.getRuleSummary(), result.getSummary());
        assertSame(aggregation.getHypotheses(), result.getHypotheses());
        assertFalse(Boolean.TRUE.equals(result.getFallback()));
        assertEquals(
                WeeklyReportVersions.RULE_PROMPT_SCHEMA_VERSION,
                result.getPromptSchemaVersion());
        verifyNoInteractions(aiService);
    }

    @Test
    void acceptsWhitelistedSuggestionAndRebuildsAllHypothesisFields() {
        featureProperties.setWeeklyReportAiEnabled(true);
        AggregationResult aggregation = aggregation();
        GenerateAgentWeeklyReportVO generated = generated("本周已形成可核验的弱信号，建议继续按规则候选开展小样本实验。", "SUG-1");
        GenerateAgentWeeklyReportVO.Hypothesis modelHypothesis = generated.getHypotheses().get(0);
        modelHypothesis.setStatement("模型自造陈述");
        modelHypothesis.setPrimaryVariable("model_variable");
        modelHypothesis.setMinimumSample(999);
        modelHypothesis.setStatus("MODEL_STATUS");
        generated.setAiCallLogId(88L);
        when(aiService.generateWeeklyCareerReport(any())).thenReturn(generated);

        NarrativeResult result = enhancer().enhance(context(), aggregation);

        ArgumentCaptor<GenerateAgentWeeklyReportDTO> captor =
                ArgumentCaptor.forClass(GenerateAgentWeeklyReportDTO.class);
        verify(aiService).generateWeeklyCareerReport(captor.capture());
        GenerateAgentWeeklyReportDTO request = captor.getValue();
        assertEquals(10L, request.getUserId());
        assertEquals("2026-07-13", request.getWeekStartDate());
        assertEquals("2026-07-19", request.getWeekEndDate());
        assertEquals("ALL", request.getTargetScopeKey());
        assertEquals("Asia/Shanghai", request.getTimezone());
        assertEquals("FACT-1", request.getFacts().get(0).getFactId());
        assertEquals("SIG-1", request.getSignals().get(0).getSignalId());
        assertEquals(aggregation.getLimits(), request.getLimits());
        assertEquals("SUG-1", request.getAllowedSuggestions().get(0).getSuggestionId());
        assertEquals(
                aggregation.getExperimentSuggestions().get(0).getHypothesis(),
                request.getAllowedSuggestions().get(0).getStatement());

        WeeklyExperimentSuggestionVO candidate = aggregation.getExperimentSuggestions().get(0);
        WeeklyReportHypothesisVO hypothesis = result.getHypotheses().get(0);
        assertEquals("AI", result.getResultSource());
        assertFalse(Boolean.TRUE.equals(result.getFallback()));
        assertEquals(88L, result.getAiCallLogId());
        assertEquals(
                WeeklyReportVersions.AI_PROMPT_SCHEMA_VERSION,
                result.getPromptSchemaVersion());
        assertEquals(candidate.getSuggestionId(), hypothesis.getHypothesisId());
        assertEquals(candidate.getHypothesis(), hypothesis.getStatement());
        assertEquals(candidate.getPrimaryVariable(), hypothesis.getPrimaryVariable());
        assertEquals(candidate.getFixedVariables(), hypothesis.getFixedVariables());
        assertEquals(candidate.getExpectedSignal(), hypothesis.getExpectedSignal());
        assertEquals(candidate.getSuccessMetric(), hypothesis.getSuccessMetric());
        assertEquals(candidate.getMinimumSample(), hypothesis.getMinimumSample());
        assertEquals(candidate.getObservationDays(), hypothesis.getObservationDays());
        assertEquals(candidate.getStopCondition(), hypothesis.getStopCondition());
        assertEquals(candidate.getConfidenceLevel(), hypothesis.getConfidenceLevel());
        assertEquals(candidate.getBasedOnSignalIds(), hypothesis.getBasedOnSignalIds());
        assertEquals(candidate.getSourceRefs(), hypothesis.getSourceRefs());
        assertEquals(candidate.getStatus(), hypothesis.getStatus());
        assertNotEquals(modelHypothesis.getStatement(), hypothesis.getStatement());
        assertNotEquals(modelHypothesis.getMinimumSample(), hypothesis.getMinimumSample());
    }

    @Test
    void fallsBackForUnknownSuggestionId() {
        featureProperties.setWeeklyReportAiEnabled(true);
        AggregationResult aggregation = aggregation();
        GenerateAgentWeeklyReportVO generated =
                generated("本周已有结构化事实，但实验建议仍需严格限制在规则白名单内。", "UNKNOWN");
        generated.setAiCallLogId(89L);
        when(aiService.generateWeeklyCareerReport(any())).thenReturn(generated);

        NarrativeResult result = enhancer().enhance(context(), aggregation);

        assertFallback(result, aggregation);
        assertTrue(result.getFallbackReason().contains("白名单"));
        assertEquals(89L, result.getAiCallLogId());
    }

    @Test
    void fallsBackWhenAiServiceThrows() {
        featureProperties.setWeeklyReportAiEnabled(true);
        AggregationResult aggregation = aggregation();
        when(aiService.generateWeeklyCareerReport(any()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        NarrativeResult result = enhancer().enhance(context(), aggregation);

        assertFallback(result, aggregation);
        assertTrue(result.getFallbackReason().contains("异常"));
    }

    @Test
    void fallsBackForNonChineseSummary() {
        featureProperties.setWeeklyReportAiEnabled(true);
        AggregationResult aggregation = aggregation();
        when(aiService.generateWeeklyCareerReport(any()))
                .thenReturn(generated("Weekly progress remains stable.", "SUG-1"));

        NarrativeResult result = enhancer().enhance(context(), aggregation);

        assertFallback(result, aggregation);
        assertTrue(result.getFallbackReason().contains("中文"));
    }

    private WeeklyReportAiEnhancer enhancer() {
        return new WeeklyReportAiEnhancer(aiService, featureProperties);
    }

    private RequestContext context() {
        RequestContext context = new RequestContext();
        context.setUserId(10L);
        context.setWeekStartDate(LocalDate.of(2026, 7, 13));
        context.setWeekEndDate(LocalDate.of(2026, 7, 19));
        context.setTargetScopeKey("ALL");
        context.setTimezone("Asia/Shanghai");
        return context;
    }

    private AggregationResult aggregation() {
        AggregationResult aggregation = new AggregationResult();
        aggregation.setRuleSummary("规则摘要：本周样本有限，建议继续积累可核验数据。");

        WeeklyReportHypothesisVO ruleHypothesis = new WeeklyReportHypothesisVO();
        ruleHypothesis.setHypothesisId("RULE-1");
        ruleHypothesis.setStatement("规则假设");
        aggregation.setHypotheses(List.of(ruleHypothesis));

        WeeklyReportFactVO fact = new WeeklyReportFactVO();
        fact.setFactId("FACT-1");
        fact.setFactType("APPLICATION_COUNT");
        fact.setLabel("本周投递数");
        fact.setValue(4);
        fact.setUnit("次");
        fact.setScope("ALL");
        fact.setTimeWindow("2026-07-13/2026-07-19");
        fact.setSourceRefs(List.of("application:1"));
        fact.setCalculationVersion("V1");
        aggregation.setFacts(List.of(fact));

        WeeklyReportSignalVO signal = new WeeklyReportSignalVO();
        signal.setSignalId("SIG-1");
        signal.setSignalType("CHANNEL");
        signal.setDirection("WEAK_POSITIVE");
        signal.setTitle("渠道出现弱信号");
        signal.setDescription("当前样本仍然有限");
        signal.setMetric(new LinkedHashMap<>(java.util.Map.of("count", 4)));
        signal.setConfidenceLevel("LOW");
        signal.setSampleBoundary(new LinkedHashMap<>(java.util.Map.of("minimum", 10)));
        signal.setScope("ALL");
        signal.setComparedScope("PREVIOUS_WEEK");
        signal.setSourceRefs(List.of("application:1"));
        signal.setBlockedConclusions(List.of("不能推断录用概率"));
        aggregation.setSignals(List.of(signal));
        aggregation.setLimits(List.of("样本不足，仅形成弱观察"));

        WeeklyExperimentSuggestionVO suggestion = new WeeklyExperimentSuggestionVO();
        suggestion.setSuggestionId("SUG-1");
        suggestion.setTitle("测试渠道变化");
        suggestion.setHypothesis("在固定简历版本和目标岗位的前提下测试渠道变化。");
        suggestion.setPrimaryVariable("channel");
        suggestion.setFixedVariables(List.of("resume_version", "target_job"));
        suggestion.setExpectedSignal("有效反馈数量变化");
        suggestion.setSuccessMetric("成熟样本中的有效反馈数");
        suggestion.setMinimumSample(10);
        suggestion.setObservationDays(14);
        suggestion.setStopCondition("达到观察期或样本门槛后复盘");
        suggestion.setConfidenceLevel("LOW");
        suggestion.setBasedOnSignalIds(List.of("SIG-1"));
        suggestion.setSourceRefs(List.of("application:1"));
        suggestion.setStatus("TO_VALIDATE");
        aggregation.setExperimentSuggestions(List.of(suggestion));
        return aggregation;
    }

    private GenerateAgentWeeklyReportVO generated(String summary, String hypothesisId) {
        GenerateAgentWeeklyReportVO generated = new GenerateAgentWeeklyReportVO();
        generated.setSummary(summary);
        GenerateAgentWeeklyReportVO.Hypothesis hypothesis =
                new GenerateAgentWeeklyReportVO.Hypothesis();
        hypothesis.setHypothesisId(hypothesisId);
        generated.setHypotheses(List.of(hypothesis));
        return generated;
    }

    private void assertFallback(NarrativeResult result, AggregationResult aggregation) {
        assertEquals("FALLBACK", result.getResultSource());
        assertTrue(Boolean.TRUE.equals(result.getFallback()));
        assertEquals(aggregation.getRuleSummary(), result.getSummary());
        assertSame(aggregation.getHypotheses(), result.getHypotheses());
        assertEquals(
                WeeklyReportVersions.AI_PROMPT_SCHEMA_VERSION,
                result.getPromptSchemaVersion());
    }
}
