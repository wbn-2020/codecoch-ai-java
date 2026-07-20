package com.codecoachai.ai.agent.weekly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.agent.domain.entity.AgentPlanAdjustment;
import com.codecoachai.ai.agent.domain.entity.AgentPlanInfluence;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlan;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlanItem;
import com.codecoachai.ai.agent.domain.entity.ReadinessScoreRecord;
import com.codecoachai.ai.agent.domain.entity.SkillGrowthSnapshot;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO;
import com.codecoachai.ai.agent.feign.WeeklyInterviewEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.WeeklyInterviewEvidenceVO;
import com.codecoachai.ai.agent.mapper.AgentPlanAdjustmentMapper;
import com.codecoachai.ai.agent.mapper.AgentPlanInfluenceMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanItemMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanMapper;
import com.codecoachai.ai.agent.mapper.ReadinessScoreRecordMapper;
import com.codecoachai.ai.agent.mapper.SkillGrowthSnapshotMapper;
import com.codecoachai.ai.agent.mapper.weekly.AgentWeeklyReportSnapshotMapper;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.EvidenceBundle;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.RequestContext;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportVersions;
import com.codecoachai.common.core.domain.Result;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportEvidenceCollector {

    private static final int MAX_PLAN_ITEMS = 500;
    private static final int MAX_ADJUSTMENTS = 500;
    private static final int MAX_INFLUENCES = 500;
    private static final int MAX_REVIEWS = 100;
    private static final int MAX_READINESS_POINTS = 100;
    private static final int MAX_SKILL_SNAPSHOTS = 500;
    private static final int MAX_COMPARABLE_WEEKS = 8;

    private final AgentWeekPlanMapper weekPlanMapper;
    private final AgentWeekPlanItemMapper weekPlanItemMapper;
    private final AgentPlanAdjustmentMapper adjustmentMapper;
    private final AgentPlanInfluenceMapper influenceMapper;
    private final AgentReviewMapper reviewMapper;
    private final ReadinessScoreRecordMapper readinessMapper;
    private final SkillGrowthSnapshotMapper skillSnapshotMapper;
    private final AgentWeeklyReportSnapshotMapper weeklyReportSnapshotMapper;
    private final WeeklyCareerEvidenceFeignClient careerEvidenceFeignClient;
    private final WeeklyInterviewEvidenceFeignClient interviewEvidenceFeignClient;

    public EvidenceBundle collect(RequestContext context) {
        EvidenceBundle bundle = new EvidenceBundle();
        AgentWeekPlan weekPlan = loadWeekPlan(context);
        bundle.setWeekPlan(weekPlan);
        if (weekPlan != null) {
            bundle.setWeekPlanItems(trim(
                    visibleAtCutoff(weekPlanItemMapper.selectList(new LambdaQueryWrapper<AgentWeekPlanItem>()
                            .eq(AgentWeekPlanItem::getUserId, context.getUserId())
                            .eq(AgentWeekPlanItem::getWeekPlanId, weekPlan.getId())
                            .eq(AgentWeekPlanItem::getDeleted, 0)
                            .le(AgentWeekPlanItem::getUpdatedAt, context.getDatabaseSourceCutoffAt())
                            .orderByAsc(AgentWeekPlanItem::getSortOrder)
                            .orderByAsc(AgentWeekPlanItem::getId)
                            .last("LIMIT " + (MAX_PLAN_ITEMS + 1))),
                             context.getDatabaseSourceCutoffAt(), AgentWeekPlanItem::getUpdatedAt),
                    MAX_PLAN_ITEMS, "AGENT_WEEK_PLAN_ITEM_TRUNCATED", bundle));
            bundle.setAdjustments(trim(
                    visibleAtCutoff(adjustmentMapper.selectList(new LambdaQueryWrapper<AgentPlanAdjustment>()
                            .eq(AgentPlanAdjustment::getUserId, context.getUserId())
                            .eq(AgentPlanAdjustment::getWeekPlanId, weekPlan.getId())
                            .eq(AgentPlanAdjustment::getDeleted, 0)
                            .ge(AgentPlanAdjustment::getOccurredAt, context.getDatabaseRangeStartAt())
                            .lt(AgentPlanAdjustment::getOccurredAt, context.getDatabaseRangeEndAt())
                            .le(AgentPlanAdjustment::getOccurredAt, context.getDatabaseSourceCutoffAt())
                            .le(AgentPlanAdjustment::getUpdatedAt, context.getDatabaseSourceCutoffAt())
                            .orderByAsc(AgentPlanAdjustment::getOccurredAt)
                            .orderByAsc(AgentPlanAdjustment::getId)
                            .last("LIMIT " + (MAX_ADJUSTMENTS + 1))),
                             context.getDatabaseSourceCutoffAt(), AgentPlanAdjustment::getUpdatedAt).stream()
                            .filter(item -> item.getOccurredAt() == null
                    || !item.getOccurredAt().isAfter(context.getDatabaseSourceCutoffAt()))
                            .toList(),
                    MAX_ADJUSTMENTS, "AGENT_PLAN_ADJUSTMENT_TRUNCATED", bundle));
            bundle.setInfluences(trim(
                    visibleAtCutoff(influenceMapper.selectList(new LambdaQueryWrapper<AgentPlanInfluence>()
                            .eq(AgentPlanInfluence::getUserId, context.getUserId())
                            .eq(AgentPlanInfluence::getWeekPlanId, weekPlan.getId())
                            .eq(AgentPlanInfluence::getDeleted, 0)
                            .le(AgentPlanInfluence::getUpdatedAt, context.getDatabaseSourceCutoffAt())
                            .orderByAsc(AgentPlanInfluence::getId)
                            .last("LIMIT " + (MAX_INFLUENCES + 1))),
                             context.getDatabaseSourceCutoffAt(), AgentPlanInfluence::getUpdatedAt),
                    MAX_INFLUENCES, "AGENT_PLAN_INFLUENCE_TRUNCATED", bundle));
        }

        LambdaQueryWrapper<AgentReview> reviewQuery = new LambdaQueryWrapper<AgentReview>()
                .eq(AgentReview::getUserId, context.getUserId())
                .eq(AgentReview::getDeleted, 0)
                .eq(AgentReview::getReviewType, "DAILY")
                .ge(AgentReview::getReviewDate, context.getWeekStartDate())
                .le(AgentReview::getReviewDate, context.getWeekEndDate())
                .le(AgentReview::getUpdatedAt, context.getDatabaseSourceCutoffAt())
                .orderByAsc(AgentReview::getReviewDate)
                .orderByDesc(AgentReview::getUpdatedAt)
                .orderByDesc(AgentReview::getId)
                .last("LIMIT " + (MAX_REVIEWS + 1));
        if (context.getTargetJobId() != null) {
            reviewQuery.eq(AgentReview::getTargetJobId, context.getTargetJobId());
        }
        bundle.setReviews(trim(visibleAtCutoff(reviewMapper.selectList(reviewQuery),
                         context.getDatabaseSourceCutoffAt(), AgentReview::getUpdatedAt).stream()
                        .filter(item -> "DAILY".equalsIgnoreCase(item.getReviewType()))
                        .toList(),
                MAX_REVIEWS, "AGENT_REVIEW_TRUNCATED", bundle));

        LambdaQueryWrapper<ReadinessScoreRecord> readinessQuery =
                new LambdaQueryWrapper<ReadinessScoreRecord>()
                        .eq(ReadinessScoreRecord::getUserId, context.getUserId())
                        .eq(ReadinessScoreRecord::getDeleted, 0)
                        .ge(ReadinessScoreRecord::getScoreDate, context.getWeekStartDate())
                        .le(ReadinessScoreRecord::getScoreDate, context.getWeekEndDate())
                        .le(ReadinessScoreRecord::getUpdatedAt, context.getDatabaseSourceCutoffAt())
                        .orderByAsc(ReadinessScoreRecord::getScoreDate)
                        .orderByAsc(ReadinessScoreRecord::getId)
                        .last("LIMIT " + (MAX_READINESS_POINTS + 1));
        if (context.getTargetJobId() != null) {
            readinessQuery.eq(ReadinessScoreRecord::getTargetJobId, context.getTargetJobId());
        }
        bundle.setReadinessRecords(trim(visibleAtCutoff(readinessMapper.selectList(readinessQuery),
                        context.getDatabaseSourceCutoffAt(), ReadinessScoreRecord::getUpdatedAt),
                MAX_READINESS_POINTS, "READINESS_SCORE_RECORD_TRUNCATED", bundle));

        bundle.setSkillSnapshots(trim(
                visibleAtCutoff(skillSnapshotMapper.selectList(new LambdaQueryWrapper<SkillGrowthSnapshot>()
                        .eq(SkillGrowthSnapshot::getUserId, context.getUserId())
                        .eq(SkillGrowthSnapshot::getDeleted, 0)
                        .ge(SkillGrowthSnapshot::getSnapshotDate, context.getWeekStartDate())
                        .le(SkillGrowthSnapshot::getSnapshotDate, context.getWeekEndDate())
                        .le(SkillGrowthSnapshot::getUpdatedAt, context.getDatabaseSourceCutoffAt())
                        .orderByAsc(SkillGrowthSnapshot::getSnapshotDate)
                        .orderByAsc(SkillGrowthSnapshot::getId)
                        .last("LIMIT " + (MAX_SKILL_SNAPSHOTS + 1))),
                         context.getDatabaseSourceCutoffAt(), SkillGrowthSnapshot::getUpdatedAt),
                MAX_SKILL_SNAPSHOTS, "SKILL_GROWTH_SNAPSHOT_TRUNCATED", bundle));

        bundle.setComparableSnapshots(weeklyReportSnapshotMapper.selectComparableCurrentSnapshots(
                context.getUserId(),
                context.getTargetScopeKey(),
                context.getTimezone(),
                context.getWeekStartDate(),
                context.getSourceCutoffAt(),
                WeeklyReportVersions.CALCULATION_VERSION,
                WeeklyReportVersions.OUTPUT_SCHEMA_VERSION,
                MAX_COMPARABLE_WEEKS));

        collectCareerEvidence(context, bundle);
        collectInterviewEvidence(context, bundle);
        return bundle;
    }

    private AgentWeekPlan loadWeekPlan(RequestContext context) {
        LambdaQueryWrapper<AgentWeekPlan> query = new LambdaQueryWrapper<AgentWeekPlan>()
                .eq(AgentWeekPlan::getUserId, context.getUserId())
                .eq(AgentWeekPlan::getWeekStartDate, context.getWeekStartDate())
                .eq(AgentWeekPlan::getDeleted, 0)
                .le(AgentWeekPlan::getUpdatedAt, context.getDatabaseSourceCutoffAt())
                .orderByDesc(AgentWeekPlan::getUpdatedAt)
                .orderByDesc(AgentWeekPlan::getId)
                .last("LIMIT 1");
        if (context.getTargetJobId() == null) {
            query.and(item -> item.eq(AgentWeekPlan::getTargetScopeKey, "ALL")
                    .or()
                    .isNull(AgentWeekPlan::getTargetJobId));
        } else {
            String id = String.valueOf(context.getTargetJobId());
            query.and(item -> item.eq(AgentWeekPlan::getTargetScopeKey, context.getTargetScopeKey())
                    .or()
                    .eq(AgentWeekPlan::getTargetScopeKey, id)
                    .or()
                    .eq(AgentWeekPlan::getTargetScopeKey, "JOB:" + id)
                    .or()
                    .eq(AgentWeekPlan::getTargetJobId, context.getTargetJobId()));
        }
        AgentWeekPlan weekPlan = weekPlanMapper.selectOne(query);
        return visibleAtCutoff(weekPlan, context.getDatabaseSourceCutoffAt(), AgentWeekPlan::getUpdatedAt)
                ? weekPlan
                : null;
    }

    private void collectCareerEvidence(RequestContext context, EvidenceBundle bundle) {
        try {
            Result<WeeklyCareerEvidenceVO> result = careerEvidenceFeignClient.getWeeklyEvidence(
                    context.getUserId(),
                    context.getRangeStartUtc(),
                    context.getRangeEndUtc(),
                    context.getSourceCutoffAt(),
                    context.getTargetJobId(),
                    context.getTimezone(),
                    null);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                markCareerUnavailable(bundle, "EMPTY_OR_FAILED_RESPONSE");
                return;
            }
            WeeklyCareerEvidenceVO evidence = result.getData();
            if (!Objects.equals(context.getUserId(), evidence.getUserId())) {
                markCareerUnavailable(bundle, "USER_SCOPE_MISMATCH");
                return;
            }
            bundle.setCareerEvidence(evidence);
        } catch (RuntimeException ex) {
            markCareerUnavailable(bundle, ex.getClass().getSimpleName());
            log.warn("Weekly career evidence unavailable userId={} scope={} failureType={}",
                    context.getUserId(), context.getTargetScopeKey(), ex.getClass().getSimpleName());
        }
    }

    private void collectInterviewEvidence(RequestContext context, EvidenceBundle bundle) {
        try {
            Result<WeeklyInterviewEvidenceVO> result = interviewEvidenceFeignClient.getWeeklyEvidence(
                    context.getUserId(),
                    context.getRangeStartUtc(),
                    context.getRangeEndUtc(),
                    context.getSourceCutoffAt(),
                    context.getTargetJobId(),
                    context.getTimezone());
            if (result == null || !result.isSuccess() || result.getData() == null) {
                markInterviewUnavailable(bundle, "EMPTY_OR_FAILED_RESPONSE");
                return;
            }
            WeeklyInterviewEvidenceVO evidence = result.getData();
            if (!Objects.equals(context.getUserId(), evidence.getUserId())) {
                markInterviewUnavailable(bundle, "USER_SCOPE_MISMATCH");
                return;
            }
            bundle.setInterviewEvidence(evidence);
        } catch (RuntimeException ex) {
            markInterviewUnavailable(bundle, ex.getClass().getSimpleName());
            log.warn("Weekly interview evidence unavailable userId={} scope={} failureType={}",
                    context.getUserId(), context.getTargetScopeKey(), ex.getClass().getSimpleName());
        }
    }

    private void markCareerUnavailable(EvidenceBundle bundle, String failureCode) {
        bundle.setCareerAvailable(false);
        bundle.setCareerFailureCode(failureCode);
    }

    private void markInterviewUnavailable(EvidenceBundle bundle, String failureCode) {
        bundle.setInterviewAvailable(false);
        bundle.setInterviewFailureCode(failureCode);
    }

    private <T> List<T> trim(List<T> values, int maxSize, String warning, EvidenceBundle bundle) {
        List<T> safe = values == null ? List.of() : values;
        if (safe.size() <= maxSize) {
            return safe;
        }
        bundle.setLocalTruncated(true);
        List<String> warnings = new ArrayList<>(bundle.getCollectionWarnings());
        warnings.add(warning);
        bundle.setCollectionWarnings(warnings);
        return new ArrayList<>(safe.subList(0, maxSize));
    }

    private <T> List<T> visibleAtCutoff(List<T> values,
                                        LocalDateTime cutoff,
                                        Function<T, LocalDateTime> updatedAt) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .filter(item -> visibleAtCutoff(item, cutoff, updatedAt))
                .toList();
    }

    private <T> boolean visibleAtCutoff(T value,
                                        LocalDateTime cutoff,
                                        Function<T, LocalDateTime> updatedAt) {
        if (value == null || cutoff == null) {
            return value != null;
        }
        LocalDateTime sourceUpdatedAt = updatedAt.apply(value);
        return sourceUpdatedAt == null || !sourceUpdatedAt.isAfter(cutoff);
    }
}
