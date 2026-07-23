package com.codecoachai.ai.agent.weekly.service;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeeklyReportAiEnhancer {

    private static final int MAX_SUMMARY_LENGTH = 800;
    private static final Pattern CHINESE_CHARACTER = Pattern.compile("[\\p{IsHan}]");

    private final AiService aiService;
    private final WeeklyReportFeatureProperties featureProperties;

    public WeeklyReportAiEnhancer(
            AiService aiService,
            WeeklyReportFeatureProperties featureProperties) {
        this.aiService = aiService;
        this.featureProperties = featureProperties;
    }

    public NarrativeResult enhance(RequestContext context, AggregationResult aggregation) {
        if (!featureProperties.isWeeklyReportAiEnabled()) {
            return ruleResult(aggregation);
        }

        try {
            GenerateAgentWeeklyReportVO generated =
                    aiService.generateWeeklyCareerReport(toRequest(context, aggregation));
            return aiResultOrFallback(generated, aggregation);
        } catch (RuntimeException ex) {
            return fallbackResult(aggregation, "AI 周报生成异常，已使用规则结果", null);
        }
    }

    private GenerateAgentWeeklyReportDTO toRequest(
            RequestContext context,
            AggregationResult aggregation) {
        GenerateAgentWeeklyReportDTO dto = new GenerateAgentWeeklyReportDTO();
        dto.setUserId(context.getUserId());
        dto.setWeekStartDate(context.getWeekStartDate() == null
                ? null : context.getWeekStartDate().toString());
        dto.setWeekEndDate(context.getWeekEndDate() == null
                ? null : context.getWeekEndDate().toString());
        dto.setTargetScopeKey(context.getTargetScopeKey());
        dto.setTimezone(context.getTimezone());

        List<GenerateAgentWeeklyReportDTO.WeeklyFact> facts = new ArrayList<>();
        for (WeeklyReportFactVO fact : safeList(aggregation.getFacts())) {
            if (fact != null) {
                facts.add(toFact(fact));
            }
        }
        dto.setFacts(facts);

        List<GenerateAgentWeeklyReportDTO.WeeklySignal> signals = new ArrayList<>();
        for (WeeklyReportSignalVO signal : safeList(aggregation.getSignals())) {
            if (signal != null) {
                signals.add(toSignal(signal));
            }
        }
        dto.setSignals(signals);
        dto.setLimits(copyList(aggregation.getLimits()));

        List<GenerateAgentWeeklyReportDTO.AllowedSuggestion> suggestions = new ArrayList<>();
        for (WeeklyExperimentSuggestionVO suggestion
                : safeList(aggregation.getExperimentSuggestions())) {
            if (suggestion != null) {
                suggestions.add(toAllowedSuggestion(suggestion));
            }
        }
        dto.setAllowedSuggestions(suggestions);
        return dto;
    }

    private GenerateAgentWeeklyReportDTO.WeeklyFact toFact(WeeklyReportFactVO source) {
        GenerateAgentWeeklyReportDTO.WeeklyFact target =
                new GenerateAgentWeeklyReportDTO.WeeklyFact();
        target.setFactId(source.getFactId());
        target.setFactType(source.getFactType());
        target.setLabel(source.getLabel());
        target.setValue(source.getValue());
        target.setUnit(source.getUnit());
        target.setScope(source.getScope());
        target.setTimeWindow(source.getTimeWindow());
        target.setSourceRefs(copyList(source.getSourceRefs()));
        target.setCalculationVersion(source.getCalculationVersion());
        return target;
    }

    private GenerateAgentWeeklyReportDTO.WeeklySignal toSignal(WeeklyReportSignalVO source) {
        GenerateAgentWeeklyReportDTO.WeeklySignal target =
                new GenerateAgentWeeklyReportDTO.WeeklySignal();
        target.setSignalId(source.getSignalId());
        target.setSignalType(source.getSignalType());
        target.setDirection(source.getDirection());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setMetric(copyMap(source.getMetric()));
        target.setConfidenceLevel(source.getConfidenceLevel());
        target.setSampleBoundary(copyMap(source.getSampleBoundary()));
        target.setScope(source.getScope());
        target.setComparedScope(source.getComparedScope());
        target.setSourceRefs(copyList(source.getSourceRefs()));
        target.setBlockedConclusions(copyList(source.getBlockedConclusions()));
        return target;
    }

    private GenerateAgentWeeklyReportDTO.AllowedSuggestion toAllowedSuggestion(
            WeeklyExperimentSuggestionVO source) {
        GenerateAgentWeeklyReportDTO.AllowedSuggestion target =
                new GenerateAgentWeeklyReportDTO.AllowedSuggestion();
        target.setSuggestionId(source.getSuggestionId());
        target.setTitle(source.getTitle());
        target.setStatement(source.getHypothesis());
        target.setPrimaryVariable(source.getPrimaryVariable());
        target.setFixedVariables(copyList(source.getFixedVariables()));
        target.setExpectedSignal(source.getExpectedSignal());
        target.setSuccessMetric(source.getSuccessMetric());
        target.setMinimumSample(source.getMinimumSample());
        target.setObservationDays(source.getObservationDays());
        target.setStopCondition(source.getStopCondition());
        target.setConfidenceLevel(source.getConfidenceLevel());
        target.setBasedOnSignalIds(copyList(source.getBasedOnSignalIds()));
        target.setSourceRefs(copyList(source.getSourceRefs()));
        return target;
    }

    private NarrativeResult aiResultOrFallback(
            GenerateAgentWeeklyReportVO generated,
            AggregationResult aggregation) {
        if (generated == null) {
            return fallbackResult(aggregation, "AI 周报结果为空，已使用规则结果", null);
        }

        String summary = StringUtils.hasText(generated.getSummary())
                ? generated.getSummary().trim() : null;
        if (!StringUtils.hasText(summary)) {
            return fallbackResult(
                    aggregation, "AI 周报摘要为空，已使用规则结果", generated.getAiCallLogId());
        }
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            return fallbackResult(
                    aggregation, "AI 周报摘要超出长度限制，已使用规则结果", generated.getAiCallLogId());
        }
        if (!CHINESE_CHARACTER.matcher(summary).find()) {
            return fallbackResult(
                    aggregation, "AI 周报摘要不是中文，已使用规则结果", generated.getAiCallLogId());
        }

        List<GenerateAgentWeeklyReportVO.Hypothesis> generatedHypotheses =
                generated.getHypotheses();
        if (generatedHypotheses == null || generatedHypotheses.isEmpty()) {
            return fallbackResult(
                    aggregation, "AI 周报未返回可用建议，已使用规则结果", generated.getAiCallLogId());
        }

        Map<String, WeeklyExperimentSuggestionVO> allowed = allowedSuggestions(aggregation);
        if (generatedHypotheses.size() > allowed.size()) {
            return fallbackResult(
                    aggregation, "AI 周报返回的建议超出规则白名单，已使用规则结果",
                    generated.getAiCallLogId());
        }

        Set<String> selectedIds = new LinkedHashSet<>();
        List<WeeklyReportHypothesisVO> hypotheses = new ArrayList<>();
        for (GenerateAgentWeeklyReportVO.Hypothesis generatedHypothesis : generatedHypotheses) {
            String suggestionId = generatedHypothesis == null
                    || !StringUtils.hasText(generatedHypothesis.getHypothesisId())
                    ? null : generatedHypothesis.getHypothesisId().trim();
            if (!StringUtils.hasText(suggestionId)) {
                return fallbackResult(
                        aggregation, "AI 周报建议编号为空，已使用规则结果",
                        generated.getAiCallLogId());
            }
            WeeklyExperimentSuggestionVO candidate = allowed.get(suggestionId);
            if (candidate == null) {
                return fallbackResult(
                        aggregation, "AI 周报返回了规则白名单之外的建议，已使用规则结果",
                        generated.getAiCallLogId());
            }
            if (!selectedIds.add(suggestionId)) {
                return fallbackResult(
                        aggregation, "AI 周报返回了重复建议，已使用规则结果",
                        generated.getAiCallLogId());
            }
            hypotheses.add(toHypothesis(candidate));
        }

        NarrativeResult result = new NarrativeResult();
        result.setSummary(summary);
        result.setHypotheses(hypotheses);
        result.setResultSource("AI");
        result.setFallback(false);
        result.setAiCallLogId(generated.getAiCallLogId());
        result.setPromptSchemaVersion(WeeklyReportVersions.AI_PROMPT_SCHEMA_VERSION);
        return result;
    }

    private Map<String, WeeklyExperimentSuggestionVO> allowedSuggestions(
            AggregationResult aggregation) {
        Map<String, WeeklyExperimentSuggestionVO> allowed = new LinkedHashMap<>();
        for (WeeklyExperimentSuggestionVO suggestion
                : safeList(aggregation.getExperimentSuggestions())) {
            if (suggestion != null && StringUtils.hasText(suggestion.getSuggestionId())) {
                allowed.putIfAbsent(suggestion.getSuggestionId().trim(), suggestion);
            }
        }
        return allowed;
    }

    private WeeklyReportHypothesisVO toHypothesis(WeeklyExperimentSuggestionVO source) {
        WeeklyReportHypothesisVO target = new WeeklyReportHypothesisVO();
        target.setHypothesisId(source.getSuggestionId());
        target.setStatement(source.getHypothesis());
        target.setPrimaryVariable(source.getPrimaryVariable());
        target.setFixedVariables(copyList(source.getFixedVariables()));
        target.setExpectedSignal(source.getExpectedSignal());
        target.setSuccessMetric(source.getSuccessMetric());
        target.setMinimumSample(source.getMinimumSample());
        target.setObservationDays(source.getObservationDays());
        target.setStopCondition(source.getStopCondition());
        target.setConfidenceLevel(source.getConfidenceLevel());
        target.setBasedOnSignalIds(copyList(source.getBasedOnSignalIds()));
        target.setSourceRefs(copyList(source.getSourceRefs()));
        target.setStatus(source.getStatus());
        return target;
    }

    private NarrativeResult ruleResult(AggregationResult aggregation) {
        NarrativeResult result = ruleNarrative(aggregation);
        result.setResultSource("RULE");
        result.setFallback(false);
        result.setPromptSchemaVersion(WeeklyReportVersions.RULE_PROMPT_SCHEMA_VERSION);
        return result;
    }

    private NarrativeResult fallbackResult(
            AggregationResult aggregation,
            String reason,
            Long aiCallLogId) {
        NarrativeResult result = ruleNarrative(aggregation);
        result.setResultSource("FALLBACK");
        result.setFallback(true);
        result.setFallbackReason(reason);
        result.setAiCallLogId(aiCallLogId);
        result.setPromptSchemaVersion(WeeklyReportVersions.AI_PROMPT_SCHEMA_VERSION);
        return result;
    }

    private NarrativeResult ruleNarrative(AggregationResult aggregation) {
        NarrativeResult result = new NarrativeResult();
        result.setSummary(aggregation.getRuleSummary());
        result.setHypotheses(aggregation.getHypotheses());
        return result;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private <T> List<T> copyList(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private <K, V> Map<K, V> copyMap(Map<K, V> values) {
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }
}
