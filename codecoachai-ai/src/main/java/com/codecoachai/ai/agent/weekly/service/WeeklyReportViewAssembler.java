package com.codecoachai.ai.agent.weekly.service;

import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReport;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSnapshot;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSource;
import com.codecoachai.ai.agent.domain.vo.weekly.AgentWeeklyReportVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyExperimentSuggestionVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyPlanDraftVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportCoverageVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportFactVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportHypothesisVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportRangeVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportSignalVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportSnapshotVersionVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklySourceCoverageItemVO;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.QueryContext;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.StoredView;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportJsonCodec;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportTimeProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WeeklyReportViewAssembler {

    private final WeeklyReportJsonCodec jsonCodec;
    private final WeeklyReportTimeProvider timeProvider;

    public AgentWeeklyReportVO notGenerated(QueryContext context, AgentWeeklyReport report) {
        AgentWeeklyReportVO vo = new AgentWeeklyReportVO();
        if (report != null) {
            vo.setId(report.getId());
            vo.setCreatedAt(report.getCreatedAt());
            vo.setUpdatedAt(report.getUpdatedAt());
        }
        vo.setTargetJobId(context.getTargetJobId());
        vo.setTargetScopeKey(context.getTargetScopeKey());
        vo.setWeekStartDate(context.getWeekStartDate());
        vo.setWeekEndDate(context.getWeekStartDate().plusDays(6));
        vo.setTimezone(context.getTimezone());
        vo.setReportStatus("NOT_GENERATED");
        vo.setSnapshotVersion(0);
        vo.setOperationResult("NOT_GENERATED");
        vo.setConfidenceLevel("FACT_ONLY");
        vo.setFallback(false);
        vo.setResultSource("RULE");
        vo.setRange(range(
                context.getWeekStartDate(),
                context.getWeekStartDate().plusDays(6),
                context.getZoneId(),
                null,
                windowStatus(context.getWeekStartDate(), context.getZoneId())));
        vo.setCoverage(new WeeklyReportCoverageVO());
        vo.setPlanDraft(unavailablePlanDraft("生成周报后才能查看下一周行动建议"));
        return vo;
    }

    public AgentWeeklyReportVO notGenerated(AgentWeeklyReport report) {
        QueryContext context = new QueryContext();
        context.setUserId(report.getUserId());
        context.setTargetJobId(report.getTargetJobId());
        context.setTargetScopeKey(report.getTargetScopeKey());
        context.setWeekStartDate(report.getWeekStartDate());
        context.setTimezone(report.getTimezone());
        context.setZoneId(java.time.ZoneId.of(report.getTimezone()));
        return notGenerated(context, report);
    }

    public AgentWeeklyReportVO toVO(StoredView stored) {
        AgentWeeklyReport report = stored.getReport();
        AgentWeeklyReportSnapshot snapshot = stored.getSnapshot();
        java.time.ZoneId zoneId = java.time.ZoneId.of(report.getTimezone());
        String currentWindowStatus = windowStatus(snapshot.getWeekStartDate(), zoneId);
        AgentWeeklyReportVO vo = new AgentWeeklyReportVO();
        vo.setId(report.getId());
        vo.setSnapshotId(snapshot.getId());
        vo.setTargetJobId(report.getTargetJobId());
        vo.setTargetScopeKey(report.getTargetScopeKey());
        vo.setWeekStartDate(snapshot.getWeekStartDate());
        vo.setWeekEndDate(snapshot.getWeekEndDate());
        vo.setTimezone(report.getTimezone());
        vo.setReportStatus(currentWindowStatus);
        vo.setSnapshotVersion(snapshot.getSnapshotVersion());
        vo.setOperationResult(stored.getOperationResult());
        vo.setSummary(snapshot.getSummary());
        vo.setConfidenceLevel(snapshot.getConfidenceLevel());
        vo.setFallback(value(snapshot.getFallback()));
        vo.setFallbackReason(snapshot.getFallbackReason());
        vo.setResultSource(snapshot.getResultSource());
        vo.setTraceId(snapshot.getTraceId());
        vo.setAiCallLogId(snapshot.getAiCallLogId());
        vo.setRange(range(
                snapshot.getWeekStartDate(),
                snapshot.getWeekEndDate(),
                zoneId,
                snapshot.getSourceCutoffAt(),
                currentWindowStatus));
        vo.setCoverage(coverage(snapshot.getCoverageJson(), stored.getSources()));
        vo.setFacts(jsonCodec.fromJson(
                snapshot.getFactsJson(),
                new TypeReference<List<WeeklyReportFactVO>>() { },
                new ArrayList<>()));
        vo.setSignals(jsonCodec.fromJson(
                snapshot.getSignalsJson(),
                new TypeReference<List<WeeklyReportSignalVO>>() { },
                new ArrayList<>()));
        vo.setHypotheses(jsonCodec.fromJson(
                snapshot.getHypothesesJson(),
                new TypeReference<List<WeeklyReportHypothesisVO>>() { },
                new ArrayList<>()));
        vo.setExperimentSuggestions(jsonCodec.fromJson(
                snapshot.getExperimentSuggestionsJson(),
                new TypeReference<List<WeeklyExperimentSuggestionVO>>() { },
                new ArrayList<>()));
        vo.setPlanDraft(planDraft(snapshot.getPlanDraftJson()));
        vo.setSnapshotHistory(history(stored.getHistory(), snapshot.getId()));
        vo.setSourceCutoffAt(snapshot.getSourceCutoffAt());
        vo.setGeneratedAt(snapshot.getGeneratedAt());
        vo.setRefreshedAt(report.getUpdatedAt());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());
        return vo;
    }

    private WeeklyReportCoverageVO coverage(
            String coverageJson,
            List<AgentWeeklyReportSource> sources) {
        WeeklyReportCoverageVO coverage = jsonCodec.fromJson(
                coverageJson, WeeklyReportCoverageVO.class, new WeeklyReportCoverageVO());
        List<WeeklySourceCoverageItemVO> sourceViews = new ArrayList<>();
        for (AgentWeeklyReportSource source : safeList(sources)) {
            WeeklySourceCoverageItemVO item = new WeeklySourceCoverageItemVO();
            item.setSourceType(source.getSourceType());
            item.setSourceId(source.getSourceId());
            item.setSourceTime(source.getSourceTime());
            item.setSourceUpdatedAt(source.getSourceUpdatedAt());
            item.setScopeKey(source.getScopeKey());
            item.setInclusionStatus(source.getInclusionStatus());
            item.setExcludeReason(source.getExcludeReason());
            item.setSourceHash(source.getSourceHash());
            item.setSafeSummary(source.getSafeSummary());
            item.setMetadata(jsonCodec.fromJson(
                    source.getMetadataJson(),
                    new TypeReference<Map<String, Object>>() { },
                    new LinkedHashMap<>()));
            sourceViews.add(item);
        }
        coverage.setSources(sourceViews);
        return coverage;
    }

    private WeeklyPlanDraftVO planDraft(String value) {
        WeeklyPlanDraftVO draft = jsonCodec.fromJson(
                value, WeeklyPlanDraftVO.class, unavailablePlanDraft("下一周计划预览暂不可用"));
        if (draft == null) {
            return unavailablePlanDraft("下一周计划预览暂不可用");
        }
        draft.setAvailable(false);
        if (!StringUtils.hasText(draft.getUnavailableReason())) {
            draft.setUnavailableReason("当前版本仅支持查看行动建议，暂不能生成计划预览");
        }
        draft.setStageFivePreviewRoute(null);
        return draft;
    }

    private WeeklyPlanDraftVO unavailablePlanDraft(String reason) {
        WeeklyPlanDraftVO draft = new WeeklyPlanDraftVO();
        draft.setAvailable(false);
        draft.setUnavailableReason(reason);
        return draft;
    }

    private List<WeeklyReportSnapshotVersionVO> history(
            List<AgentWeeklyReportSnapshot> snapshots,
            Long currentSnapshotId) {
        List<WeeklyReportSnapshotVersionVO> result = new ArrayList<>();
        for (AgentWeeklyReportSnapshot snapshot : safeList(snapshots)) {
            WeeklyReportSnapshotVersionVO item = new WeeklyReportSnapshotVersionVO();
            item.setSnapshotId(snapshot.getId());
            item.setSnapshotVersion(snapshot.getSnapshotVersion());
            item.setReportStatus(snapshot.getReportStatus());
            item.setConfidenceLevel(snapshot.getConfidenceLevel());
            item.setResultSource(snapshot.getResultSource());
            item.setFallback(value(snapshot.getFallback()));
            item.setSourceCutoffAt(snapshot.getSourceCutoffAt());
            item.setGeneratedAt(snapshot.getGeneratedAt());
            item.setCurrent(Objects.equals(currentSnapshotId, snapshot.getId()));
            result.add(item);
        }
        return result;
    }

    private WeeklyReportRangeVO range(
            LocalDate weekStart,
            LocalDate weekEnd,
            java.time.ZoneId zoneId,
            LocalDateTime cutoff,
            String status) {
        WeeklyReportRangeVO range = new WeeklyReportRangeVO();
        range.setWeekStartDate(weekStart);
        range.setWeekEndDate(weekEnd);
        range.setRangeStartUtc(LocalDateTime.ofInstant(
                weekStart.atStartOfDay(zoneId).toInstant(), ZoneOffset.UTC));
        range.setRangeEndUtc(LocalDateTime.ofInstant(
                weekStart.plusDays(7).atStartOfDay(zoneId).toInstant(), ZoneOffset.UTC));
        range.setSourceCutoffAt(cutoff);
        range.setTimezone(zoneId.getId());
        range.setWindowStatus(status);
        return range;
    }

    private String windowStatus(LocalDate weekStart, java.time.ZoneId zoneId) {
        Instant now = timeProvider.now();
        Instant rangeEnd = weekStart.plusDays(7).atStartOfDay(zoneId).toInstant();
        return now.isBefore(rangeEnd) ? "IN_PROGRESS" : "COMPLETED";
    }

    private boolean value(Integer value) {
        return value != null && value == 1;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
