package com.codecoachai.ai.agent.weekly.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSnapshot;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportFactVO;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO.ApplicationEventItem;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO.ApplicationItem;
import com.codecoachai.ai.agent.feign.WeeklyInterviewEvidenceVO;
import com.codecoachai.ai.agent.feign.WeeklyInterviewEvidenceVO.ComparisonGroupItem;
import com.codecoachai.ai.agent.weekly.config.WeeklyReportFeatureProperties;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.AggregationResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.EvidenceBundle;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.RequestContext;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportHashUtils;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportJsonCodec;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyReportRuleEngineTest {

    @Test
    void lowSampleStaysFactOnlyAndKeepsChineseBoundary() {
        RequestContext context = context(9701L, LocalDateTime.of(2026, 7, 19, 1, 0));
        EvidenceBundle evidence = evidence();
        for (long id = 1; id <= 4; id++) {
            evidence.getCareerEvidence().getApplications().add(application(
                    id, "CHANNEL:官网", 100L, LocalDateTime.of(2026, 7, 10, 8, 0)));
        }

        AggregationResult result = engine(true).aggregate(context, evidence);

        assertEquals("FACT_ONLY", result.getConfidenceLevel());
        assertEquals(4, numberFact(result, "application.activity.count"));
        assertTrue(result.getSignals().stream()
                .anyMatch(signal -> "application.sample.boundary".equals(signal.getSignalId())));
        assertTrue(result.getLimits().stream().anyMatch(value -> value.contains("少于 5 条")));
        assertFalse(Boolean.TRUE.equals(result.getPlanDraft().getAvailable()));
        assertNotNull(result.getInputHash());
        assertNotNull(result.getGenerationFingerprint());
    }

    @Test
    void explicitOutcomeMaturesRecentApplicationWithoutTreatingOtherRecentItemAsNoResponse() {
        RequestContext context = context(9701L, LocalDateTime.of(2026, 7, 19, 1, 0));
        EvidenceBundle evidence = evidence();
        evidence.getCareerEvidence().getApplications().add(application(
                1L, "CHANNEL:官网", 100L, LocalDateTime.of(2026, 7, 12, 8, 0)));
        evidence.getCareerEvidence().getApplications().add(application(
                2L, "CHANNEL:官网", 100L, LocalDateTime.of(2026, 7, 12, 8, 0)));
        evidence.getCareerEvidence().getApplications().add(application(
                3L, "CHANNEL:官网", 100L, LocalDateTime.of(2026, 7, 10, 8, 0)));
        evidence.getCareerEvidence().getApplicationEvents().add(event(
                11L, 1L, "REJECTED", LocalDateTime.of(2026, 7, 18, 8, 0)));

        AggregationResult result = engine(false).aggregate(context, evidence);

        assertEquals(2, numberFact(result, "application.matured.count"));
        assertEquals(1, numberFact(result, "application.immature.count"));
        assertEquals(1, numberFact(result, "application.verified_response.count"));
        assertTrue(result.getLimits().stream()
                .anyMatch(value -> value.contains("不计为无反馈")));
    }

    @Test
    void dailyReviewsKeepLatestDailyRecordAndExcludeTaskReview() {
        RequestContext context = context(9701L, LocalDateTime.of(2026, 7, 19, 1, 0));
        EvidenceBundle evidence = evidence();
        evidence.setReviews(List.of(
                review(1L, "DAILY", LocalDate.of(2026, 7, 15),
                        LocalDateTime.of(2026, 7, 15, 20, 0), 1, 0, 2),
                review(2L, "DAILY", LocalDate.of(2026, 7, 15),
                        LocalDateTime.of(2026, 7, 15, 22, 0), 2, 0, 1),
                review(3L, "TASK", LocalDate.of(2026, 7, 16),
                        LocalDateTime.of(2026, 7, 16, 22, 0), 9, 0, 0)));

        AggregationResult result = engine(false).aggregate(context, evidence);

        assertEquals(1, numberFact(result, "review.daily.count"));
        assertEquals(3, numberFact(result, "review.task.count"));
        assertEquals(2, numberFact(result, "review.task.done.count"));
        assertEquals(2, result.getSources().stream()
                .filter(source -> "AGENT_REVIEW".equals(source.getSourceType()))
                .filter(source -> "EXCLUDED".equals(source.getInclusionStatus()))
                .count());
    }

    @Test
    void allScopeBlocksCrossJobComparisonEvenWithEnoughChannelSamples() {
        RequestContext context = context(null, LocalDateTime.of(2026, 7, 19, 1, 0));
        EvidenceBundle evidence = evidence();
        addMaturedChannelSamples(evidence, "CHANNEL:官网", 1L, 5);
        addMaturedChannelSamples(evidence, "CHANNEL:内推", 101L, 5);
        addComparableWeeks(evidence, 2);
        addComparableInterviewGroup(evidence, 3);
        evidence.setReviews(List.of(review(
                9L,
                "DAILY",
                LocalDate.of(2026, 7, 18),
                LocalDateTime.of(2026, 7, 18, 22, 0),
                2,
                0,
                1)));

        AggregationResult result = engine(true).aggregate(context, evidence);

        assertEquals("MEDIUM", result.getConfidenceLevel());
        assertFalse(result.getSignals().stream()
                .anyMatch(signal -> "application.channel.observation".equals(signal.getSignalId())));
        assertTrue(result.getExperimentSuggestions().stream()
                .anyMatch(item -> "TARGET_JOB".equals(item.getPrimaryVariable())));
        assertTrue(result.getLimits().stream()
                .anyMatch(value -> value.contains("全部岗位范围")));
    }

    @Test
    void targetScopeAllowsChannelWeakObservationAtFiveMaturedItemsPerGroup() {
        RequestContext context = context(9701L, LocalDateTime.of(2026, 7, 19, 1, 0));
        EvidenceBundle evidence = evidence();
        addMaturedChannelSamples(evidence, "CHANNEL:官网", 1L, 5);
        addMaturedChannelSamples(evidence, "CHANNEL:内推", 101L, 5);

        AggregationResult result = engine(false).aggregate(context, evidence);

        assertTrue(result.getSignals().stream()
                .anyMatch(signal -> "application.channel.observation".equals(signal.getSignalId())));
        assertTrue(result.getExperimentSuggestions().stream()
                .anyMatch(item -> "CHANNEL".equals(item.getPrimaryVariable())));
        assertTrue(result.getExperimentSuggestions().size() <= 2);
    }

    @Test
    void sameEvidenceKeepsInputHashAcrossCutoffChangesButPromptModeChangesFingerprint() {
        EvidenceBundle evidence = evidence();
        addMaturedChannelSamples(evidence, "CHANNEL:官网", 1L, 5);
        RequestContext first = context(9701L, LocalDateTime.of(2026, 7, 19, 1, 0));
        RequestContext second = context(9701L, LocalDateTime.of(2026, 7, 19, 2, 0));

        AggregationResult firstAi = engine(true).aggregate(first, evidence);
        AggregationResult secondAi = engine(true).aggregate(second, evidence);
        AggregationResult secondRule = engine(false).aggregate(second, evidence);

        assertEquals(firstAi.getInputHash(), secondAi.getInputHash());
        assertEquals(firstAi.getGenerationFingerprint(), secondAi.getGenerationFingerprint());
        assertEquals(secondAi.getInputHash(), secondRule.getInputHash());
        assertNotEquals(secondAi.getGenerationFingerprint(), secondRule.getGenerationFingerprint());
    }

    @Test
    void partialSourcesAreAuditedInChineseAndLowerConfidence() {
        RequestContext context = context(9701L, LocalDateTime.of(2026, 7, 19, 1, 0));
        EvidenceBundle evidence = evidence();
        evidence.setCareerAvailable(false);
        evidence.setCareerFailureCode("SocketTimeoutException");
        evidence.setCareerEvidence(null);

        AggregationResult result = engine(true).aggregate(context, evidence);

        assertEquals("FACT_ONLY", result.getConfidenceLevel());
        assertEquals("PARTIAL", result.getCoverage().getConsistencyLevel());
        assertTrue(result.getCoverage().getWarnings().stream()
                .anyMatch(value -> value.contains("投递、日历和策略实验来源")));
        assertTrue(result.getSources().stream()
                .anyMatch(source -> "UNAVAILABLE".equals(source.getInclusionStatus())
                        && "WEEKLY_CAREER_EVIDENCE".equals(source.getSourceType())));
        assertTrue(result.getRuleSummary().contains("来源不可用"));
    }

    private WeeklyReportRuleEngine engine(boolean aiEnabled) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        WeeklyReportJsonCodec codec = new WeeklyReportJsonCodec(objectMapper);
        WeeklyReportFeatureProperties properties = new WeeklyReportFeatureProperties();
        properties.setWeeklyReportEnabled(true);
        properties.setWeeklyReportAiEnabled(aiEnabled);
        return new WeeklyReportRuleEngine(
                new WeeklyReportSamplePolicy(),
                new WeeklyReportHashUtils(codec),
                codec,
                new WeeklyReportSanitizer(),
                properties);
    }

    private RequestContext context(Long targetJobId, LocalDateTime cutoff) {
        RequestContext context = new RequestContext();
        context.setUserId(10L);
        context.setTargetJobId(targetJobId);
        context.setTargetScopeKey(targetJobId == null ? "ALL" : "TARGET_JOB:" + targetJobId);
        context.setWeekStartDate(LocalDate.of(2026, 7, 13));
        context.setWeekEndDate(LocalDate.of(2026, 7, 19));
        context.setTimezone("Asia/Shanghai");
        context.setZoneId(ZoneId.of("Asia/Shanghai"));
        context.setRangeStartUtc(LocalDateTime.of(2026, 7, 12, 16, 0));
        context.setRangeEndUtc(LocalDateTime.of(2026, 7, 19, 16, 0));
        context.setSourceCutoffAt(cutoff);
        context.setGeneratedAt(cutoff);
        context.setReportStatus("IN_PROGRESS");
        context.setOperation("GENERATE");
        return context;
    }

    private EvidenceBundle evidence() {
        EvidenceBundle evidence = new EvidenceBundle();
        evidence.setCareerAvailable(true);
        evidence.setInterviewAvailable(true);
        evidence.setCareerEvidence(new WeeklyCareerEvidenceVO());
        evidence.setInterviewEvidence(new WeeklyInterviewEvidenceVO());
        return evidence;
    }

    private ApplicationItem application(
            Long id,
            String channel,
            Long resumeVersionId,
            LocalDateTime appliedAt) {
        ApplicationItem item = new ApplicationItem();
        item.setApplicationId(id);
        item.setTargetJobId(9701L);
        item.setChannelKey(channel);
        item.setSource(channel);
        item.setResumeVersionId(resumeVersionId);
        item.setAppliedAt(appliedAt);
        item.setUpdatedAt(appliedAt);
        item.setCurrentStatus("APPLIED");
        item.setIncluded(true);
        item.setSourceHash("sha256:application-" + id);
        item.setSafeSummary("投递记录：状态=APPLIED");
        return item;
    }

    private ApplicationEventItem event(
            Long eventId,
            Long applicationId,
            String eventType,
            LocalDateTime eventTime) {
        ApplicationEventItem item = new ApplicationEventItem();
        item.setEventId(eventId);
        item.setApplicationId(applicationId);
        item.setTargetJobId(9701L);
        item.setEventType(eventType);
        item.setEventTime(eventTime);
        item.setUpdatedAt(eventTime);
        item.setIncluded(true);
        item.setSourceHash("sha256:event-" + eventId);
        item.setSafeSummary("投递事件：类型=" + eventType);
        return item;
    }

    private AgentReview review(
            Long id,
            String type,
            LocalDate reviewDate,
            LocalDateTime updatedAt,
            int done,
            int skipped,
            int todo) {
        AgentReview review = new AgentReview();
        review.setId(id);
        review.setUserId(10L);
        review.setTargetJobId(9701L);
        review.setTargetScopeKey("TARGET_JOB:9701");
        review.setReviewType(type);
        review.setReviewDate(reviewDate);
        review.setDoneCount(done);
        review.setSkippedCount(skipped);
        review.setTodoCount(todo);
        review.setUpdatedAt(updatedAt);
        return review;
    }

    private void addMaturedChannelSamples(
            EvidenceBundle evidence,
            String channel,
            long firstId,
            int count) {
        for (int index = 0; index < count; index++) {
            long id = firstId + index;
            evidence.getCareerEvidence().getApplications().add(application(
                    id,
                    channel,
                    100L,
                    LocalDateTime.of(2026, 7, 9, 8, 0).plusHours(index)));
            if (index % 2 == 0) {
                evidence.getCareerEvidence().getApplicationEvents().add(event(
                        10_000L + id,
                        id,
                        "INTERVIEW_INVITED",
                        LocalDateTime.of(2026, 7, 18, 8, 0).plusMinutes(index)));
            }
        }
    }

    private void addComparableWeeks(EvidenceBundle evidence, int count) {
        List<AgentWeeklyReportSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            AgentWeeklyReportSnapshot snapshot = new AgentWeeklyReportSnapshot();
            snapshot.setId(500L + index);
            snapshot.setTargetScopeKey("ALL");
            snapshot.setFactsJson("[{\"factId\":\"application.activity.count\",\"value\":8}]");
            snapshots.add(snapshot);
        }
        evidence.setComparableSnapshots(snapshots);
    }

    private void addComparableInterviewGroup(EvidenceBundle evidence, int count) {
        ComparisonGroupItem group = new ComparisonGroupItem();
        group.setComparisonKey("9701|V1|D1");
        group.setTargetJobId(9701L);
        group.setTrustedReportCount(count);
        group.setDirection("UP");
        group.setSourceReportIds(List.of(1L, 2L, 3L));
        evidence.getInterviewEvidence().setComparisonGroups(List.of(group));
    }

    private int numberFact(AggregationResult result, String factId) {
        WeeklyReportFactVO fact = result.getFacts().stream()
                .filter(item -> factId.equals(item.getFactId()))
                .findFirst()
                .orElseThrow();
        return ((Number) fact.getValue()).intValue();
    }
}
