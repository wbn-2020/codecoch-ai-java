package com.codecoachai.ai.agent.weekly.service;

import com.codecoachai.ai.agent.domain.entity.AgentPlanAdjustment;
import com.codecoachai.ai.agent.domain.entity.AgentPlanInfluence;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlan;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlanItem;
import com.codecoachai.ai.agent.domain.entity.ReadinessScoreRecord;
import com.codecoachai.ai.agent.domain.entity.SkillGrowthSnapshot;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSnapshot;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSource;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyExperimentSuggestionVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyPlanDraftItemVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyPlanDraftVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportCoverageVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportFactVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportHypothesisVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyReportSignalVO;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO.ApplicationEventItem;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO.ApplicationItem;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO.CalendarEventItem;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO.ExperimentItem;
import com.codecoachai.ai.agent.feign.WeeklyCareerEvidenceVO.ExperimentRelationItem;
import com.codecoachai.ai.agent.feign.WeeklyInterviewEvidenceVO;
import com.codecoachai.ai.agent.feign.WeeklyInterviewEvidenceVO.ComparisonGroupItem;
import com.codecoachai.ai.agent.feign.WeeklyInterviewEvidenceVO.ReportItem;
import com.codecoachai.ai.agent.feign.WeeklyInterviewEvidenceVO.SessionItem;
import com.codecoachai.ai.agent.weekly.config.WeeklyReportFeatureProperties;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.AggregationResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.EvidenceBundle;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.RequestContext;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportHashUtils;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportJsonCodec;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportSanitizer;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportVersions;
import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WeeklyReportRuleEngine {

    private static final int MAX_SOURCE_REFS = 60;
    private static final int MAX_SIGNALS = 20;
    private static final int MAX_SUGGESTIONS = 2;
    private static final Set<String> MATURITY_EVENT_TYPES = Set.of(
            "INTERVIEW_INVITED", "INTERVIEW_COMPLETED", "OFFER", "REJECTED", "CLOSED");
    private static final Set<String> VERIFIED_RESPONSE_EVENT_TYPES = Set.of(
            "INTERVIEW_INVITED", "INTERVIEW_COMPLETED", "OFFER", "REJECTED");

    private final WeeklyReportSamplePolicy samplePolicy;
    private final WeeklyReportHashUtils hashUtils;
    private final WeeklyReportJsonCodec jsonCodec;
    private final WeeklyReportSanitizer sanitizer;
    private final WeeklyReportFeatureProperties featureProperties;

    public AggregationResult aggregate(RequestContext context, EvidenceBundle evidence) {
        Objects.requireNonNull(context, "周报请求上下文不能为空");
        EvidenceBundle bundle = evidence == null ? new EvidenceBundle() : evidence;
        Selection<AgentWeekPlanItem> planSelection = selectPlanItems(context, bundle.getWeekPlanItems());
        Selection<AgentReview> reviewSelection = selectDailyReviews(context, bundle.getReviews());
        MetricState state = calculateMetrics(context, bundle, planSelection.items(), reviewSelection.items());

        List<AgentWeeklyReportSource> sources =
                buildSources(context, bundle, planSelection, reviewSelection);
        WeeklyReportCoverageVO coverage = buildCoverage(bundle, sources);

        WeeklyReportSamplePolicy.Decision decision = samplePolicy.evaluate(sampleMetrics(context, bundle, state));
        RefLimiter refLimiter = new RefLimiter();
        List<WeeklyReportFactVO> facts = buildFacts(context, state, refLimiter);
        List<String> limits = buildLimits(context, bundle, state, decision, coverage);
        List<WeeklyReportSignalVO> signals =
                buildSignals(context, state, decision, refLimiter);
        List<WeeklyExperimentSuggestionVO> suggestions =
                buildSuggestions(context, bundle, state, decision);
        List<WeeklyReportHypothesisVO> hypotheses = suggestions.stream()
                .map(this::toHypothesis)
                .toList();
        WeeklyPlanDraftVO planDraft = buildPlanDraft(context, suggestions);

        if (refLimiter.truncated) {
            addDistinct(coverage.getWarnings(), "单项事实的来源引用较多，页面仅展示前 60 条，完整记录保留在来源覆盖清单中");
            addDistinct(limits, "部分事实的来源引用已在展示层截断，计算仍使用全部纳入来源");
        }
        if (!"HIGH".equals(decision.getConfidenceLevel())) {
            addDistinct(coverage.getWarnings(), confidenceWarning(decision.getConfidenceLevel()));
        }

        AggregationResult result = new AggregationResult();
        result.setFacts(facts);
        result.setSignals(signals);
        result.setHypotheses(hypotheses);
        result.setExperimentSuggestions(suggestions);
        result.setPlanDraft(planDraft);
        result.setCoverage(coverage);
        result.setSources(sources);
        result.setLimits(limits);
        result.setConfidenceLevel(decision.getConfidenceLevel());
        result.setRuleSummary(buildRuleSummary(state, decision, bundle));

        String inputHash = hashUtils.hash(inputFingerprint(
                context, sources, coverage, facts, signals, suggestions, limits));
        String promptVersion = featureProperties.isWeeklyReportAiEnabled()
                ? WeeklyReportVersions.AI_PROMPT_SCHEMA_VERSION
                : WeeklyReportVersions.RULE_PROMPT_SCHEMA_VERSION;
        String generationFingerprint = hashUtils.hash(List.of(
                inputHash,
                WeeklyReportVersions.CALCULATION_VERSION,
                promptVersion,
                WeeklyReportVersions.OUTPUT_SCHEMA_VERSION));
        result.setInputHash(inputHash);
        result.setGenerationFingerprint(generationFingerprint);
        return result;
    }

    private Selection<AgentWeekPlanItem> selectPlanItems(
            RequestContext context,
            List<AgentWeekPlanItem> values) {
        Selection<AgentWeekPlanItem> selection = new Selection<>();
        List<AgentWeekPlanItem> sorted = safeList(values).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(AgentWeekPlanItem::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AgentWeekPlanItem::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Set<String> seen = new LinkedHashSet<>();
        int row = 0;
        for (AgentWeekPlanItem item : sorted) {
            LocalDate plannedDate = firstNonNull(item.getPlannedDate(), item.getDueDate());
            if (plannedDate != null
                    && (plannedDate.isBefore(context.getWeekStartDate())
                    || plannedDate.isAfter(context.getWeekEndDate()))) {
                selection.exclude(item, "OUTSIDE_ACTIVITY_WINDOW");
                continue;
            }
            String key = item.getAgentTaskId() != null
                    ? "TASK:" + item.getAgentTaskId()
                    : item.getId() != null ? "ITEM:" + item.getId() : "ROW:" + row++;
            if (!seen.add(key)) {
                selection.exclude(item, "DUPLICATE_PLAN_TASK");
                continue;
            }
            selection.include(item);
        }
        return selection;
    }

    private Selection<AgentReview> selectDailyReviews(
            RequestContext context,
            List<AgentReview> values) {
        Selection<AgentReview> selection = new Selection<>();
        List<AgentReview> sorted = safeList(values).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(AgentReview::getReviewDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AgentReview::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgentReview::getId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Set<String> seen = new LinkedHashSet<>();
        for (AgentReview review : sorted) {
            if (!"DAILY".equalsIgnoreCase(review.getReviewType())) {
                selection.exclude(review, "REVIEW_TYPE_NOT_DAILY");
                continue;
            }
            if (review.getReviewDate() == null
                    || review.getReviewDate().isBefore(context.getWeekStartDate())
                    || review.getReviewDate().isAfter(context.getWeekEndDate())) {
                selection.exclude(review, "OUTSIDE_ACTIVITY_WINDOW");
                continue;
            }
            String scope = firstText(
                    review.getTargetScopeKey(),
                    review.getTargetJobId() == null ? "ALL" : "TARGET_JOB:" + review.getTargetJobId());
            String key = review.getReviewDate() + "|" + scope;
            if (!seen.add(key)) {
                selection.exclude(review, "DUPLICATE_DAILY_REVIEW");
                continue;
            }
            selection.include(review);
        }
        return selection;
    }

    private MetricState calculateMetrics(
            RequestContext context,
            EvidenceBundle bundle,
            List<AgentWeekPlanItem> planItems,
            List<AgentReview> dailyReviews) {
        MetricState state = new MetricState();
        state.planItems = planItems;
        state.dailyReviews = dailyReviews;
        state.adjustments = safeList(bundle.getAdjustments());
        state.influences = safeList(bundle.getInfluences());
        state.readinessRecords = safeList(bundle.getReadinessRecords()).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(ReadinessScoreRecord::getScoreDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReadinessScoreRecord::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        state.skillSnapshots = safeList(bundle.getSkillSnapshots());
        state.comparableSnapshots = safeList(bundle.getComparableSnapshots());

        for (AgentWeekPlanItem item : planItems) {
            String status = normalizeCode(item.getItemStatus(), "TODO");
            if ("DONE".equals(status)) {
                state.planDoneCount++;
            } else if ("SKIPPED".equals(status) || "CANCELLED".equals(status)) {
                state.planSkippedCount++;
            } else if ("DEFERRED".equals(status)) {
                state.planDeferredCount++;
            } else {
                state.planTodoCount++;
            }
        }
        for (AgentReview review : dailyReviews) {
            state.dailyDoneCount += value(review.getDoneCount());
            state.dailySkippedCount += value(review.getSkippedCount());
            state.dailyTodoCount += value(review.getTodoCount());
        }
        state.dailyTaskCount =
                state.dailyDoneCount + state.dailySkippedCount + state.dailyTodoCount;

        WeeklyCareerEvidenceVO career = bundle.getCareerEvidence();
        if (career != null) {
            state.applications = distinctIncluded(
                    career.getApplications(), ApplicationItem::getApplicationId, ApplicationItem::getIncluded);
            state.applicationEvents = distinctIncluded(
                    career.getApplicationEvents(),
                    ApplicationEventItem::getEventId,
                    ApplicationEventItem::getIncluded);
            state.calendarEvents = distinctIncluded(
                    career.getCalendarEvents(), CalendarEventItem::getEventId, CalendarEventItem::getIncluded);
            state.experiments = distinctIncluded(
                    career.getExperiments(), ExperimentItem::getExperimentId, ExperimentItem::getIncluded);
        }

        LocalDateTime maturedBefore = context.getSourceCutoffAt() == null
                ? context.getRangeEndUtc().minusDays(7)
                : context.getSourceCutoffAt().minusDays(7);
        for (ApplicationItem application : state.applications) {
            LocalDateTime businessTime = applicationBusinessTime(application);
            if (application.getApplicationId() != null
                    && businessTime != null
                    && !businessTime.isAfter(maturedBefore)) {
                state.maturedApplicationIds.add(application.getApplicationId());
            }
        }
        for (ApplicationEventItem event : state.applicationEvents) {
            String eventType = normalizeCode(event.getEventType(), "UNKNOWN");
            if (event.getApplicationId() != null && MATURITY_EVENT_TYPES.contains(eventType)) {
                state.maturedApplicationIds.add(event.getApplicationId());
            }
            if (event.getApplicationId() != null
                    && VERIFIED_RESPONSE_EVENT_TYPES.contains(eventType)) {
                state.verifiedResponseApplicationIds.add(event.getApplicationId());
            }
            if (event.getApplicationId() != null && "FOLLOW_UP".equals(eventType)) {
                state.followUpApplicationIds.add(event.getApplicationId());
            }
            if ("INTERVIEW_INVITED".equals(eventType)) {
                state.realInterviewInviteCount++;
            }
            if ("INTERVIEW_COMPLETED".equals(eventType)) {
                state.realInterviewCompletedCount++;
            }
        }
        state.immatureApplicationCount = (int) state.applications.stream()
                .map(ApplicationItem::getApplicationId)
                .filter(Objects::nonNull)
                .filter(id -> !state.maturedApplicationIds.contains(id))
                .distinct()
                .count();

        for (ApplicationItem application : state.applications) {
            Long applicationId = application.getApplicationId();
            String channel = sanitizer.channelKey(application.getChannelKey(), application.getSource());
            String resumeVersion = sanitizer.resumeVersionKey(application.getResumeVersionId());
            updateSegment(state.channelSegments, channel, applicationId, state);
            updateSegment(state.resumeVersionSegments, resumeVersion, applicationId, state);
        }

        for (CalendarEventItem event : state.calendarEvents) {
            String eventType = normalizeCode(event.getEventType(), "OTHER_CALENDAR_EVENT");
            if ("CANCELLED_CALENDAR_EVENT".equals(eventType)
                    || "CANCELLED".equalsIgnoreCase(event.getStatus())) {
                state.calendarCancelledCount++;
            } else {
                state.calendarPlannedCount++;
            }
            if (event.getApplicationId() == null) {
                state.calendarUnlinkedCount++;
            }
        }

        for (ExperimentItem experiment : state.experiments) {
            for (ExperimentRelationItem relation : safeList(experiment.getRelations())) {
                if (relation != null && Boolean.TRUE.equals(relation.getIncluded())) {
                    state.includedExperimentRelationCount++;
                }
            }
        }

        WeeklyInterviewEvidenceVO interview = bundle.getInterviewEvidence();
        if (interview != null) {
            state.interviewSessions = distinctIncluded(
                    interview.getSessions(), SessionItem::getSessionId, SessionItem::getIncluded);
            state.interviewReports = distinctIncluded(
                    interview.getReports(), ReportItem::getReportId, ReportItem::getIncluded);
            state.comparisonGroups = safeList(interview.getComparisonGroups()).stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(
                            ComparisonGroupItem::getComparisonKey,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }
        state.trustedComparableInterviewCount = state.comparisonGroups.stream()
                .map(ComparisonGroupItem::getTrustedReportCount)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElseGet(() -> trustedReportGroupMaximum(state.interviewReports));
        state.comparableWeekCount = 1 + state.comparableSnapshots.size();
        state.latestReadinessScore = state.readinessRecords.isEmpty()
                ? null : state.readinessRecords.get(state.readinessRecords.size() - 1).getScore();
        AgentWeeklyReportSnapshot previous = state.comparableSnapshots.isEmpty()
                ? null : state.comparableSnapshots.get(0);
        state.previousReadinessScore = historicalNumber(previous, "readiness.latest.score");
        state.previousApplicationActivityCount =
                historicalNumber(previous, "application.activity.count");
        return state;
    }

    private WeeklyReportSamplePolicy.Metrics sampleMetrics(
            RequestContext context,
            EvidenceBundle bundle,
            MetricState state) {
        WeeklyReportSamplePolicy.Metrics metrics = new WeeklyReportSamplePolicy.Metrics();
        metrics.setApplicationActivityCount(state.applications.size());
        metrics.setMaturedApplicationCount(state.maturedApplicationIds.size());
        metrics.setTrustedComparableInterviewCount(state.trustedComparableInterviewCount);
        metrics.setComparableWeekCount(state.comparableWeekCount);
        metrics.setDailyTaskCount(state.dailyTaskCount);
        metrics.setDailyDoneCount(state.dailyDoneCount);
        metrics.setAllScope(context.getTargetJobId() == null);
        metrics.setCareerUnavailable(!Boolean.TRUE.equals(bundle.getCareerAvailable()));
        metrics.setInterviewUnavailable(!Boolean.TRUE.equals(bundle.getInterviewAvailable()));
        metrics.setDailyEvidenceInsufficient(
                state.dailyTaskCount < WeeklyReportSamplePolicy.MIN_DAILY_TASKS
                        || state.dailyDoneCount < WeeklyReportSamplePolicy.MIN_DAILY_DONE_TASKS);
        metrics.setTruncated(Boolean.TRUE.equals(bundle.getLocalTruncated())
                || bundle.getCareerEvidence() != null
                && Boolean.TRUE.equals(bundle.getCareerEvidence().getTruncated())
                || bundle.getInterviewEvidence() != null
                && Boolean.TRUE.equals(bundle.getInterviewEvidence().getTruncated()));
        return metrics;
    }

    private List<WeeklyReportFactVO> buildFacts(
            RequestContext context,
            MetricState state,
            RefLimiter limiter) {
        List<WeeklyReportFactVO> facts = new ArrayList<>();
        String window = "[" + context.getWeekStartDate() + ","
                + context.getWeekStartDate().plusDays(7) + ")";
        String scope = context.getTargetScopeKey();

        addFact(facts, "plan.task.count", "COUNT", "本周计划任务数",
                state.planItems.size(), "项", scope, window,
                refs("AGENT_WEEK_PLAN_ITEM", ids(state.planItems, AgentWeekPlanItem::getId), limiter));
        addFact(facts, "plan.task.done.count", "COUNT", "本周已完成计划任务数",
                state.planDoneCount, "项", scope, window,
                refsForPlanStatus(state.planItems, Set.of("DONE"), limiter));
        addFact(facts, "plan.task.deferred.count", "COUNT", "本周延后计划任务数",
                state.planDeferredCount, "项", scope, window,
                refsForPlanStatus(state.planItems, Set.of("DEFERRED"), limiter));
        addFact(facts, "plan.adjustment.count", "COUNT", "本周计划调整记录数",
                state.adjustments.size(), "条", scope, window,
                refs("AGENT_PLAN_ADJUSTMENT", ids(state.adjustments, AgentPlanAdjustment::getId), limiter));
        addFact(facts, "review.daily.count", "COUNT", "本周有效每日复盘数",
                state.dailyReviews.size(), "天", scope, window,
                refs("AGENT_REVIEW", ids(state.dailyReviews, AgentReview::getId), limiter));
        addFact(facts, "review.task.count", "COUNT", "每日复盘记录任务数",
                state.dailyTaskCount, "项", scope, window,
                refs("AGENT_REVIEW", ids(state.dailyReviews, AgentReview::getId), limiter));
        addFact(facts, "review.task.done.count", "COUNT", "每日复盘记录完成任务数",
                state.dailyDoneCount, "项", scope, window,
                refs("AGENT_REVIEW", ids(state.dailyReviews, AgentReview::getId), limiter));

        addFact(facts, "application.activity.count", "COUNT", "本周投递活动数",
                state.applications.size(), "条", scope, window,
                refs("JOB_APPLICATION", ids(state.applications, ApplicationItem::getApplicationId), limiter));
        addFact(facts, "application.event.count", "COUNT", "本周投递事件数",
                state.applicationEvents.size(), "条", scope, window,
                refs("JOB_APPLICATION_EVENT",
                        ids(state.applicationEvents, ApplicationEventItem::getEventId), limiter));
        addFact(facts, "application.matured.count", "COUNT", "成熟投递样本数",
                state.maturedApplicationIds.size(), "条", scope, window,
                maturedRefs(state, limiter));
        addFact(facts, "application.immature.count", "COUNT", "未到观察期投递数",
                state.immatureApplicationCount, "条", scope, window,
                refs("JOB_APPLICATION", ids(state.applications, ApplicationItem::getApplicationId), limiter));
        addFact(facts, "application.verified_response.count", "COUNT", "已有明确反馈的成熟投递数",
                state.verifiedResponseApplicationIds.size(), "条", scope, window,
                eventRefs(state.applicationEvents, VERIFIED_RESPONSE_EVENT_TYPES, limiter));
        addFact(facts, "application.verified_response.rate", "RATE", "成熟投递已记录反馈率",
                percent(state.verifiedResponseApplicationIds.size(), state.maturedApplicationIds.size()),
                "%", scope, window,
                eventRefs(state.applicationEvents, VERIFIED_RESPONSE_EVENT_TYPES, limiter));
        addFact(facts, "application.follow_up.count", "COUNT", "本周有跟进记录的投递数",
                state.followUpApplicationIds.size(), "条", scope, window,
                eventRefs(state.applicationEvents, Set.of("FOLLOW_UP"), limiter));
        addFact(facts, "application.interview_invite.count", "COUNT", "本周真实面试邀请事件数",
                state.realInterviewInviteCount, "次", scope, window,
                eventRefs(state.applicationEvents, Set.of("INTERVIEW_INVITED"), limiter));
        addFact(facts, "application.interview_completed.count", "COUNT", "本周真实面试完成事件数",
                state.realInterviewCompletedCount, "次", scope, window,
                eventRefs(state.applicationEvents, Set.of("INTERVIEW_COMPLETED"), limiter));

        addFact(facts, "calendar.planned.count", "COUNT", "本周求职日程安排数",
                state.calendarPlannedCount, "项", scope, window,
                calendarRefs(state.calendarEvents, false, limiter));
        addFact(facts, "calendar.cancelled.count", "COUNT", "本周已取消求职日程数",
                state.calendarCancelledCount, "项", scope, window,
                calendarRefs(state.calendarEvents, true, limiter));
        addFact(facts, "calendar.unlinked.count", "COUNT", "未关联投递的日程数",
                state.calendarUnlinkedCount, "项", scope, window,
                refs("CAREER_CALENDAR_EVENT",
                        ids(state.calendarEvents.stream()
                                        .filter(item -> item.getApplicationId() == null).toList(),
                                CalendarEventItem::getEventId),
                        limiter));
        addFact(facts, "experiment.count", "COUNT", "本周覆盖策略实验数",
                state.experiments.size(), "个", scope, window,
                refs("JOB_SEARCH_EXPERIMENT",
                        ids(state.experiments, ExperimentItem::getExperimentId), limiter));
        addFact(facts, "experiment.relation.count", "COUNT", "有效实验关联记录数",
                state.includedExperimentRelationCount, "条", scope, window,
                experimentRelationRefs(state.experiments, limiter));

        addFact(facts, "interview.session.count", "COUNT", "本周模拟面试会话数",
                state.interviewSessions.size(), "次", scope, window,
                refs("INTERVIEW_SESSION", ids(state.interviewSessions, SessionItem::getSessionId), limiter));
        addFact(facts, "interview.trusted_report.count", "COUNT", "本周可信可比面试报告数",
                state.interviewReports.size(), "份", scope, window,
                refs("INTERVIEW_REPORT", ids(state.interviewReports, ReportItem::getReportId), limiter));
        addFact(facts, "interview.comparable_group.max_count", "COUNT", "单一可比组最大可信报告数",
                state.trustedComparableInterviewCount, "份", scope, window,
                refs("INTERVIEW_REPORT", ids(state.interviewReports, ReportItem::getReportId), limiter));

        addFact(facts, "readiness.point.count", "COUNT", "本周准备度记录点数",
                state.readinessRecords.size(), "个", scope, window,
                refs("READINESS_SCORE_RECORD",
                        ids(state.readinessRecords, ReadinessScoreRecord::getId), limiter));
        if (state.latestReadinessScore != null) {
            ReadinessScoreRecord latest =
                    state.readinessRecords.get(state.readinessRecords.size() - 1);
            addFact(facts, "readiness.latest.score", "SCORE", "本周最新准备度",
                    state.latestReadinessScore, "分", scope, window,
                    refs("READINESS_SCORE_RECORD", List.of(latest.getId()), limiter));
        }
        addFact(facts, "skill.snapshot.count", "COUNT", "本周技能成长快照数",
                state.skillSnapshots.size(), "个", scope, window,
                refs("SKILL_GROWTH_SNAPSHOT",
                        ids(state.skillSnapshots, SkillGrowthSnapshot::getId), limiter));
        addFact(facts, "weekly.comparable.count", "COUNT", "含本周的可比周数量",
                state.comparableWeekCount, "周", scope, window,
                refs("AGENT_WEEKLY_REPORT_SNAPSHOT",
                        ids(state.comparableSnapshots, AgentWeeklyReportSnapshot::getId), limiter));
        return facts;
    }

    private List<WeeklyReportSignalVO> buildSignals(
            RequestContext context,
            MetricState state,
            WeeklyReportSamplePolicy.Decision decision,
            RefLimiter limiter) {
        List<WeeklyReportSignalVO> signals = new ArrayList<>();
        String scope = context.getTargetScopeKey();

        if (state.applications.size() < WeeklyReportSamplePolicy.MIN_STRATEGY_APPLICATIONS) {
            WeeklyReportSignalVO signal = signal(
                    "application.sample.boundary",
                    "APPLICATION_SAMPLE",
                    "MIXED",
                    "投递样本仍在积累",
                    state.applications.size() < WeeklyReportSamplePolicy.MIN_WEAK_APPLICATIONS
                            ? "当前投递活动少于 5 条，只能陈述发生过的事实，暂不判断策略方向。"
                            : "当前投递活动已出现弱观察基础，但尚未达到 10 条可比较样本，暂不判断策略优劣。",
                    decision.getConfidenceLevel(),
                    scope,
                    decision.getBlockedConclusions());
            signal.getMetric().put("ACTIVITY_COUNT", state.applications.size());
            signal.getMetric().put("TARGET_SAMPLE", WeeklyReportSamplePolicy.MIN_STRATEGY_APPLICATIONS);
            signal.getSampleBoundary().put(
                    "MINIMUM_SAMPLE", WeeklyReportSamplePolicy.MIN_STRATEGY_APPLICATIONS);
            signal.setSourceRefs(refs(
                    "JOB_APPLICATION", ids(state.applications, ApplicationItem::getApplicationId), limiter));
            signals.add(signal);
        }

        if (Boolean.TRUE.equals(decision.getWeakObservationAllowed())
                && !state.maturedApplicationIds.isEmpty()) {
            WeeklyReportSignalVO signal = signal(
                    "application.response.observation",
                    "APPLICATION_RESPONSE",
                    "OBSERVED",
                    "成熟投递已形成反馈观察",
                    "在当前记录范围内，成熟投递中已有明确反馈事件；该比例只代表已记录事实，不能证明策略有效性。",
                    decision.getConfidenceLevel(),
                    scope,
                    decision.getBlockedConclusions());
            signal.getMetric().put("MATURED_APPLICATIONS", state.maturedApplicationIds.size());
            signal.getMetric().put(
                    "VERIFIED_RESPONSE_RATE",
                    percent(state.verifiedResponseApplicationIds.size(),
                            state.maturedApplicationIds.size()));
            signal.getSampleBoundary().put(
                    "MINIMUM_SAMPLE", WeeklyReportSamplePolicy.MIN_STRATEGY_APPLICATIONS);
            signal.setSourceRefs(eventRefs(
                    state.applicationEvents, VERIFIED_RESPONSE_EVENT_TYPES, limiter));
            signals.add(signal);
        }

        if (samplePolicy.channelComparisonAllowed(
                toPolicySegments(state.channelSegments),
                context.getTargetJobId() == null)) {
            WeeklyReportSignalVO signal = signal(
                    "application.channel.observation",
                    "CHANNEL_RESPONSE",
                    "MIXED",
                    "渠道分组出现待验证差异",
                    "在当前目标岗位和简历版本边界内，渠道分组的已记录反馈率出现方向差异；该差异仅用于设计下一轮单变量验证。",
                    decision.getConfidenceLevel(),
                    scope,
                    decision.getBlockedConclusions());
            signal.getMetric().put("VERIFIED_RESPONSE_RATE", segmentRates(state.channelSegments));
            signal.getSampleBoundary().put(
                    "MINIMUM_SAMPLE", WeeklyReportSamplePolicy.MIN_CHANNEL_MATURED_APPLICATIONS);
            signal.setSourceRefs(segmentRefs(state, state.channelSegments, limiter));
            signals.add(signal);
        }

        if (samplePolicy.resumeVersionComparisonAllowed(
                toPolicySegments(state.resumeVersionSegments),
                context.getTargetJobId() == null)) {
            WeeklyReportSignalVO signal = signal(
                    "application.resume_version.observation",
                    "RESUME_VERSION_RESPONSE",
                    "MIXED",
                    "简历版本分组出现待验证差异",
                    "在目标岗位和渠道边界保持一致的前提下，简历版本分组出现反馈方向差异；该观察不能直接解释为版本质量高低。",
                    decision.getConfidenceLevel(),
                    scope,
                    decision.getBlockedConclusions());
            signal.getMetric().put(
                    "VERIFIED_RESPONSE_RATE", segmentRates(state.resumeVersionSegments));
            signal.getSampleBoundary().put(
                    "MINIMUM_SAMPLE", WeeklyReportSamplePolicy.MIN_RESUME_VERSION_USES);
            signal.setSourceRefs(segmentRefs(state, state.resumeVersionSegments, limiter));
            signals.add(signal);
        }

        if (Boolean.TRUE.equals(decision.getInterviewTrendAllowed())) {
            ComparisonGroupItem group = state.comparisonGroups.stream()
                    .filter(item -> value(item.getTrustedReportCount())
                            >= WeeklyReportSamplePolicy.MIN_COMPARABLE_INTERVIEWS)
                    .findFirst()
                    .orElse(null);
            if (group != null) {
                WeeklyReportSignalVO signal = signal(
                        "interview.ability.observation",
                        "INTERVIEW_ABILITY",
                        normalizeDirection(group.getDirection()),
                        "可比面试报告形成能力变化观察",
                        "同一目标岗位、量表和维度的可信报告已达到观察门槛，当前只展示方向变化，不外推真实招聘结果。",
                        decision.getConfidenceLevel(),
                        scope,
                        decision.getBlockedConclusions());
                signal.getMetric().put("BASELINE_VALUE", group.getFirstScore());
                signal.getMetric().put("CURRENT_VALUE", group.getLastScore());
                signal.getMetric().put("SAMPLE_COUNT", group.getTrustedReportCount());
                signal.getSampleBoundary().put(
                        "MINIMUM_SAMPLE", WeeklyReportSamplePolicy.MIN_COMPARABLE_INTERVIEWS);
                signal.setSourceRefs(refs(
                        "INTERVIEW_REPORT", safeList(group.getSourceReportIds()), limiter));
                signals.add(signal);
            }
        }

        if (Boolean.TRUE.equals(decision.getDailyExecutionTrendAllowed())) {
            WeeklyReportSignalVO signal = signal(
                    "task.execution.observation",
                    "TASK_COMPLETION",
                    "OBSERVED",
                    "每日复盘形成任务执行观察",
                    "每日复盘中的任务数量和完成数量已达到最低门槛，该观察只说明系统内任务执行情况。",
                    decision.getConfidenceLevel(),
                    scope,
                    decision.getBlockedConclusions());
            signal.getMetric().put(
                    "TASK_COMPLETION_RATE", percent(state.dailyDoneCount, state.dailyTaskCount));
            signal.getMetric().put("COMPLETED_COUNT", state.dailyDoneCount);
            signal.getMetric().put("TOTAL_COUNT", state.dailyTaskCount);
            signal.getSampleBoundary().put(
                    "MINIMUM_SAMPLE", WeeklyReportSamplePolicy.MIN_DAILY_TASKS);
            signal.setSourceRefs(refs(
                    "AGENT_REVIEW", ids(state.dailyReviews, AgentReview::getId), limiter));
            signals.add(signal);
        }

        if (Boolean.TRUE.equals(decision.getMultiWeekTrendAllowed())
                && state.previousApplicationActivityCount != null) {
            int current = state.applications.size();
            int previous = state.previousApplicationActivityCount;
            WeeklyReportSignalVO signal = signal(
                    "application.multi_week.activity",
                    "APPLICATION_ACTIVITY",
                    direction(current, previous),
                    "投递活动出现周度变化",
                    "本周投递活动与上一份可比周报出现数量变化，该变化不代表结果质量或策略有效性。",
                    decision.getConfidenceLevel(),
                    scope,
                    decision.getBlockedConclusions());
            signal.getMetric().put("BASELINE_VALUE", previous);
            signal.getMetric().put("CURRENT_VALUE", current);
            signal.getSampleBoundary().put(
                    "MINIMUM_SAMPLE", WeeklyReportSamplePolicy.MIN_COMPARABLE_WEEKS);
            signal.setSourceRefs(combineRefs(
                    refs("JOB_APPLICATION",
                            ids(state.applications, ApplicationItem::getApplicationId), limiter),
                    refs("AGENT_WEEKLY_REPORT_SNAPSHOT",
                            ids(state.comparableSnapshots, AgentWeeklyReportSnapshot::getId), limiter),
                    limiter));
            signals.add(signal);
        }

        if (state.latestReadinessScore != null && state.previousReadinessScore != null) {
            WeeklyReportSignalVO signal = signal(
                    "readiness.weekly.change",
                    "READINESS_SCORE",
                    direction(state.latestReadinessScore, state.previousReadinessScore),
                    "准备度出现周度变化",
                    "准备度只反映系统内训练和任务证据，不代表 Offer 概率或招聘方评价。",
                    decision.getConfidenceLevel(),
                    scope,
                    decision.getBlockedConclusions());
            signal.getMetric().put("BASELINE_VALUE", state.previousReadinessScore);
            signal.getMetric().put("CURRENT_VALUE", state.latestReadinessScore);
            signal.setSourceRefs(combineRefs(
                    refs("READINESS_SCORE_RECORD",
                            ids(state.readinessRecords, ReadinessScoreRecord::getId), limiter),
                    refs("AGENT_WEEKLY_REPORT_SNAPSHOT",
                            ids(state.comparableSnapshots, AgentWeeklyReportSnapshot::getId), limiter),
                    limiter));
            signals.add(signal);
        }
        return signals.stream().limit(MAX_SIGNALS).toList();
    }

    private List<WeeklyExperimentSuggestionVO> buildSuggestions(
            RequestContext context,
            EvidenceBundle bundle,
            MetricState state,
            WeeklyReportSamplePolicy.Decision decision) {
        List<WeeklyExperimentSuggestionVO> result = new ArrayList<>();
        if (!Boolean.TRUE.equals(bundle.getCareerAvailable())) {
            result.add(suggestion(
                    context,
                    "career-evidence",
                    "补齐投递与日历记录后再判断策略",
                    "本次投递、日历或实验来源不可用，下一轮先补齐系统内事实记录，再观察同一目标岗位下的变化。",
                    "PROJECT_EVIDENCE",
                    List.of("TARGET_JOB"),
                    "来源恢复后能够形成完整的投递与日历事实",
                    "APPLICATION_ACTIVITY_COUNT",
                    1,
                    7,
                    "来源仍不可用或记录归属无法确认时停止形成策略比较",
                    "FACT_ONLY",
                    List.of(),
                    List.of()));
        }
        if (result.size() < MAX_SUGGESTIONS
                && state.applications.size() < WeeklyReportSamplePolicy.MIN_STRATEGY_APPLICATIONS) {
            result.add(suggestion(
                    context,
                    "application-sample",
                    "保持当前边界，继续补齐投递样本",
                    "固定目标岗位、渠道和简历版本，继续记录投递时间与明确反馈事件，达到 10 条可比较样本后再观察方向。",
                    "APPLICATION_TIMING",
                    List.of("TARGET_JOB", "CHANNEL", "RESUME_VERSION"),
                    "形成至少 10 条边界一致的投递记录",
                    "MATURED_APPLICATION_COUNT",
                    WeeklyReportSamplePolicy.MIN_STRATEGY_APPLICATIONS,
                    7,
                    "目标岗位、渠道或简历版本发生同时变化时暂停比较",
                    decision.getConfidenceLevel(),
                    List.of("application.sample.boundary"),
                    refsWithoutLimit("JOB_APPLICATION",
                            ids(state.applications, ApplicationItem::getApplicationId))));
        }
        if (result.size() < MAX_SUGGESTIONS
                && context.getTargetJobId() == null) {
            result.add(suggestion(
                    context,
                    "target-scope",
                    "先选择一个目标岗位范围做下一轮观察",
                    "当前周报为全部岗位范围，下一轮先固定一个目标岗位，并保持渠道和简历版本边界稳定。",
                    "TARGET_JOB",
                    List.of("CHANNEL", "RESUME_VERSION"),
                    "形成岗位范围明确的事实与成熟样本",
                    "MATURED_APPLICATION_COUNT",
                    WeeklyReportSamplePolicy.MIN_WEAK_APPLICATIONS,
                    7,
                    "目标岗位发生变化时重新建立观察范围",
                    "LOW",
                    List.of(),
                    List.of()));
        }
        if (result.size() < MAX_SUGGESTIONS
                && samplePolicy.channelComparisonAllowed(
                        state.channelSegments, context.getTargetJobId() == null)) {
            WeeklyExperimentSuggestionVO suggestion = suggestion(
                    context,
                    "channel",
                    "固定岗位和简历版本，继续验证渠道差异",
                    "在同一目标岗位和同一简历版本下，继续记录两个渠道各至少 5 条成熟投递，观察已记录反馈率是否出现方向差异。",
                    "CHANNEL",
                    List.of("TARGET_JOB", "RESUME_VERSION"),
                    "渠道分组的已记录反馈率形成可复核方向",
                    "VERIFIED_RESPONSE_RATE",
                    WeeklyReportSamplePolicy.MIN_STRATEGY_APPLICATIONS,
                    7,
                    "任一渠道样本边界变化或同时更换简历版本时暂停比较",
                    decision.getConfidenceLevel(),
                    List.of("application.channel.observation"),
                    segmentRefsWithoutLimit(state, state.channelSegments));
            suggestion.setEligibleSegments(eligibleSegments(
                    state.channelSegments, "channelKey",
                    WeeklyReportSamplePolicy.MIN_CHANNEL_MATURED_APPLICATIONS));
            result.add(suggestion);
        }
        if (result.size() < MAX_SUGGESTIONS
                && samplePolicy.resumeVersionComparisonAllowed(
                        state.resumeVersionSegments, context.getTargetJobId() == null)) {
            WeeklyExperimentSuggestionVO suggestion = suggestion(
                    context,
                    "resume-version",
                    "固定岗位和渠道，继续验证简历版本差异",
                    "在同一目标岗位和渠道下，每个简历版本继续积累至少 3 条成熟投递，观察已记录反馈事件的方向差异。",
                    "RESUME_VERSION",
                    List.of("TARGET_JOB", "CHANNEL"),
                    "简历版本分组形成可复核的反馈事实",
                    "VERIFIED_RESPONSE_RATE",
                    WeeklyReportSamplePolicy.MIN_STRATEGY_APPLICATIONS,
                    7,
                    "渠道或目标岗位变化时暂停版本比较",
                    decision.getConfidenceLevel(),
                    List.of("application.resume_version.observation"),
                    segmentRefsWithoutLimit(state, state.resumeVersionSegments));
            suggestion.setEligibleSegments(eligibleSegments(
                    state.resumeVersionSegments, "resumeVersionKey",
                    WeeklyReportSamplePolicy.MIN_RESUME_VERSION_USES));
            result.add(suggestion);
        }
        if (result.size() < MAX_SUGGESTIONS
                && Boolean.TRUE.equals(decision.getInterviewTrendAllowed())) {
            result.add(suggestion(
                    context,
                    "interview-training",
                    "固定训练主题，继续采集可信面试报告",
                    "保持目标岗位、量表和训练主题一致，继续完成可信模拟面试，观察同一可比组中的弱项和分数方向。",
                    "TRAINING_TOPIC",
                    List.of("TARGET_JOB", "INTERVIEW_TOPIC"),
                    "同一可比组形成持续的面试能力观察",
                    "INTERVIEW_COMPLETION_RATE",
                    WeeklyReportSamplePolicy.MIN_COMPARABLE_INTERVIEWS,
                    7,
                    "量表、目标岗位或维度集合变化时停止比较",
                    decision.getConfidenceLevel(),
                    List.of("interview.ability.observation"),
                    refsWithoutLimit(
                            "INTERVIEW_REPORT",
                            ids(state.interviewReports, ReportItem::getReportId))));
        }
        if (result.isEmpty()) {
            result.add(suggestion(
                    context,
                    "daily-evidence",
                    "继续完成任务并记录每日复盘",
                    "下一周保持任务范围清晰，至少记录 3 条任务并完成其中 2 条，再观察系统内执行变化。",
                    "TRAINING_TOPIC",
                    List.of("TARGET_JOB"),
                    "形成满足最低门槛的每日任务与复盘证据",
                    "TASK_COMPLETION_RATE",
                    WeeklyReportSamplePolicy.MIN_DAILY_TASKS,
                    7,
                    "任务主题或目标岗位发生变化时重新建立观察范围",
                    decision.getConfidenceLevel(),
                    List.of("task.execution.observation"),
                    refsWithoutLimit("AGENT_REVIEW",
                            ids(state.dailyReviews, AgentReview::getId))));
        }
        return result.stream().limit(MAX_SUGGESTIONS).toList();
    }

    private List<String> buildLimits(
            RequestContext context,
            EvidenceBundle bundle,
            MetricState state,
            WeeklyReportSamplePolicy.Decision decision,
            WeeklyReportCoverageVO coverage) {
        List<String> limits = new ArrayList<>();
        if (!Boolean.TRUE.equals(bundle.getCareerAvailable())) {
            limits.add("投递、日历或策略实验来源本次不可用，相关比较和策略建议已关闭");
        }
        if (!Boolean.TRUE.equals(bundle.getInterviewAvailable())) {
            limits.add("面试来源本次不可用，不能形成面试能力变化结论");
        }
        if (context.getTargetJobId() == null) {
            limits.add("当前为全部岗位范围，只展示分组事实，不比较跨岗位策略优劣");
        }
        if (state.applications.size() < WeeklyReportSamplePolicy.MIN_WEAK_APPLICATIONS) {
            limits.add("投递活动少于 5 条，当前仅展示事实");
        } else if (state.applications.size() < WeeklyReportSamplePolicy.MIN_STRATEGY_APPLICATIONS) {
            limits.add("投递活动少于 10 条，只允许低置信度弱观察");
        }
        if (state.maturedApplicationIds.isEmpty()) {
            limits.add("当前没有成熟结果样本，未到观察期的投递不计为无反馈");
        } else if (state.maturedApplicationIds.size()
                < WeeklyReportSamplePolicy.MIN_STRATEGY_APPLICATIONS) {
            limits.add("成熟投递少于 10 条，暂不比较渠道或策略方向");
        }
        if (state.immatureApplicationCount > 0) {
            limits.add("有 " + state.immatureApplicationCount
                    + " 条投递尚未到观察期，这些记录不计为无反馈");
        }
        if (state.trustedComparableInterviewCount
                < WeeklyReportSamplePolicy.MIN_COMPARABLE_INTERVIEWS) {
            limits.add("同一岗位、量表和维度的可信面试报告少于 3 份，暂不形成面试能力趋势");
        }
        if (state.comparableWeekCount < WeeklyReportSamplePolicy.MIN_COMPARABLE_WEEKS) {
            limits.add("可比周少于 3 周，当前变化不能解释为稳定趋势");
        }
        if (state.dailyTaskCount < WeeklyReportSamplePolicy.MIN_DAILY_TASKS
                || state.dailyDoneCount < WeeklyReportSamplePolicy.MIN_DAILY_DONE_TASKS) {
            limits.add("每日复盘任务少于 3 条或完成少于 2 条，计划执行趋势证据不足");
        }
        if (Boolean.TRUE.equals(coverage.getTruncated())) {
            limits.add("部分来源达到查询上限，本次结果按已返回记录汇总，强结论已降级");
        }
        limits.add("所有变化信号只代表当前记录范围内的观察，不能证明因果关系");
        return limits.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private String buildRuleSummary(
            MetricState state,
            WeeklyReportSamplePolicy.Decision decision,
            EvidenceBundle bundle) {
        StringBuilder summary = new StringBuilder();
        summary.append("本周记录 ")
                .append(state.applications.size())
                .append(" 条投递活动、")
                .append(state.applicationEvents.size())
                .append(" 条投递事件、")
                .append(state.dailyReviews.size())
                .append(" 天有效每日复盘和 ")
                .append(state.interviewReports.size())
                .append(" 份可信可比面试报告。");
        if (!Boolean.TRUE.equals(bundle.getCareerAvailable())
                || !Boolean.TRUE.equals(bundle.getInterviewAvailable())) {
            summary.append(" 本次存在来源不可用，相关区域已关闭比较并保留来源限制。");
        } else if ("FACT_ONLY".equals(decision.getConfidenceLevel())) {
            summary.append(" 当前缺少成熟或最低投递样本，仅陈述已发生事实。");
        } else if ("LOW".equals(decision.getConfidenceLevel())) {
            summary.append(" 当前样本仍偏少，只显示弱观察，下一轮应优先补齐边界一致的记录。");
        } else {
            summary.append(" 当前可形成带样本边界的方向观察，但所有差异仍需通过单变量实验继续验证。");
        }
        return summary.toString();
    }

    private List<AgentWeeklyReportSource> buildSources(
            RequestContext context,
            EvidenceBundle bundle,
            Selection<AgentWeekPlanItem> planSelection,
            Selection<AgentReview> reviewSelection) {
        List<AgentWeeklyReportSource> sources = new ArrayList<>();
        AgentWeekPlan weekPlan = bundle.getWeekPlan();
        if (weekPlan != null) {
            sources.add(source(
                    "AGENT_WEEK_PLAN",
                    weekPlan.getId(),
                    weekPlan.getGeneratedAt(),
                    weekPlan.getUpdatedAt(),
                    context.getTargetScopeKey(),
                    "INCLUDED",
                    null,
                    localHash("AGENT_WEEK_PLAN", weekPlan.getId(), weekPlan.getSnapshotVersion(),
                            weekPlan.getPlanStatus(), weekPlan.getUpdatedAt()),
                    "Agent 周计划：状态=" + normalizeCode(weekPlan.getPlanStatus(), "UNKNOWN"),
                    canonicalMap("snapshotVersion", weekPlan.getSnapshotVersion(),
                            "fallback", weekPlan.getFallback())));
        }
        for (AgentWeekPlanItem item : safeList(bundle.getWeekPlanItems())) {
            boolean included = planSelection.isIncluded(item);
            sources.add(source(
                    "AGENT_WEEK_PLAN_ITEM",
                    item.getId(),
                    localDateTime(context, firstNonNull(item.getPlannedDate(), item.getDueDate())),
                    item.getUpdatedAt(),
                    context.getTargetScopeKey(),
                    included ? "INCLUDED" : "EXCLUDED",
                    included ? null : planSelection.reason(item),
                    localHash("AGENT_WEEK_PLAN_ITEM", item.getId(), item.getAgentTaskId(),
                            item.getItemStatus(), item.getPlannedDate(), item.getDueDate(),
                            item.getSnapshotVersion(), item.getUpdatedAt()),
                    "周计划项：状态=" + normalizeCode(item.getItemStatus(), "TODO"),
                    canonicalMap("actionType", safeCode(item.getActionType()),
                            "fallback", item.getFallback(),
                            "sampleInsufficient", item.getSampleInsufficient())));
        }
        for (AgentPlanAdjustment item : safeList(bundle.getAdjustments())) {
            sources.add(source(
                    "AGENT_PLAN_ADJUSTMENT",
                    item.getId(),
                    item.getOccurredAt(),
                    item.getUpdatedAt(),
                    context.getTargetScopeKey(),
                    "INCLUDED",
                    null,
                    localHash("AGENT_PLAN_ADJUSTMENT", item.getId(), item.getWeekPlanItemId(),
                            item.getAgentTaskId(), item.getAdjustmentType(), item.getFromStatus(),
                            item.getToStatus(), item.getOccurredAt(), item.getUpdatedAt()),
                    "计划调整：类型=" + normalizeCode(item.getAdjustmentType(), "UNKNOWN"),
                    canonicalMap("fromStatus", safeCode(item.getFromStatus()),
                            "toStatus", safeCode(item.getToStatus()))));
        }
        for (AgentPlanInfluence item : safeList(bundle.getInfluences())) {
            sources.add(source(
                    "AGENT_PLAN_INFLUENCE",
                    item.getId(),
                    item.getCreatedAt(),
                    item.getUpdatedAt(),
                    context.getTargetScopeKey(),
                    "INCLUDED",
                    null,
                    localHash("AGENT_PLAN_INFLUENCE", item.getId(), item.getWeekPlanItemId(),
                            item.getSourceType(), item.getSourceId(), item.getInfluenceStrength(),
                            item.getSnapshotVersion(), item.getSnapshotHash(), item.getUpdatedAt()),
                    "计划影响记录：来源类型=" + normalizeCode(item.getSourceType(), "UNKNOWN"),
                    canonicalMap("strength", safeCode(item.getInfluenceStrength()),
                            "fallback", item.getFallback())));
        }
        for (AgentReview item : safeList(bundle.getReviews())) {
            boolean included = reviewSelection.isIncluded(item);
            sources.add(source(
                    "AGENT_REVIEW",
                    item.getId(),
                    localDateTime(context, item.getReviewDate()),
                    item.getUpdatedAt(),
                    firstText(item.getTargetScopeKey(), context.getTargetScopeKey()),
                    included ? "INCLUDED" : "EXCLUDED",
                    included ? null : reviewSelection.reason(item),
                    localHash("AGENT_REVIEW", item.getId(), item.getReviewDate(),
                            item.getReviewType(), item.getReviewVersion(), item.getDoneCount(),
                            item.getSkippedCount(), item.getTodoCount(),
                            item.getSourceSnapshotHash(), item.getUpdatedAt()),
                    "每日复盘：完成=" + value(item.getDoneCount())
                            + "，跳过=" + value(item.getSkippedCount())
                            + "，待办=" + value(item.getTodoCount()),
                    canonicalMap("reviewType", safeCode(item.getReviewType()),
                            "confidenceLevel", safeCode(item.getConfidenceLevel()),
                            "fallback", item.getFallback())));
        }
        for (ReadinessScoreRecord item : safeList(bundle.getReadinessRecords())) {
            sources.add(source(
                    "READINESS_SCORE_RECORD",
                    item.getId(),
                    localDateTime(context, item.getScoreDate()),
                    item.getUpdatedAt(),
                    scopeForTarget(context, item.getTargetJobId()),
                    "INCLUDED",
                    null,
                    localHash("READINESS_SCORE_RECORD", item.getId(), item.getScoreDate(),
                            item.getScore(), item.getTaskCompletionRate(),
                            item.getAgentSuccessRate(), item.getUpdatedAt()),
                    "准备度记录：分数=" + item.getScore(),
                    canonicalMap("score", item.getScore())));
        }
        for (SkillGrowthSnapshot item : safeList(bundle.getSkillSnapshots())) {
            sources.add(source(
                    "SKILL_GROWTH_SNAPSHOT",
                    item.getId(),
                    localDateTime(context, item.getSnapshotDate()),
                    item.getUpdatedAt(),
                    context.getTargetScopeKey(),
                    "INCLUDED",
                    null,
                    localHash("SKILL_GROWTH_SNAPSHOT", item.getId(), item.getSnapshotDate(),
                            item.getSkillCode(), item.getScore(), item.getTaskCount(),
                            item.getDoneCount(), item.getUpdatedAt()),
                    "技能成长快照：分数=" + item.getScore()
                            + "，任务数=" + value(item.getTaskCount()),
                    canonicalMap("skillCode", safeCode(item.getSkillCode()),
                            "sourceType", safeCode(item.getSourceType()))));
        }
        for (AgentWeeklyReportSnapshot item : safeList(bundle.getComparableSnapshots())) {
            sources.add(source(
                    "AGENT_WEEKLY_REPORT_SNAPSHOT",
                    item.getId(),
                    item.getGeneratedAt(),
                    item.getUpdatedAt(),
                    item.getTargetScopeKey(),
                    "INCLUDED",
                    null,
                    firstText(item.getInputHash(),
                            localHash("AGENT_WEEKLY_REPORT_SNAPSHOT", item.getId(),
                                    item.getSnapshotVersion(), item.getUpdatedAt())),
                    "历史可比周报：版本=" + item.getSnapshotVersion(),
                    canonicalMap("weekStartDate", item.getWeekStartDate(),
                            "calculationVersion", item.getCalculationVersion())));
        }
        appendCareerSources(context, bundle.getCareerEvidence(), sources);
        appendInterviewSources(context, bundle.getInterviewEvidence(), sources);
        appendAvailabilitySources(context, bundle, sources);
        sources.sort(Comparator
                .comparing(AgentWeeklyReportSource::getSourceType,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AgentWeeklyReportSource::getSourceId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AgentWeeklyReportSource::getInclusionStatus,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AgentWeeklyReportSource::getSourceHash,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return sources;
    }

    private void appendCareerSources(
            RequestContext context,
            WeeklyCareerEvidenceVO evidence,
            List<AgentWeeklyReportSource> sources) {
        if (evidence == null) {
            return;
        }
        for (ApplicationItem item : safeList(evidence.getApplications())) {
            sources.add(source(
                    "JOB_APPLICATION",
                    item.getApplicationId(),
                    applicationBusinessTime(item),
                    item.getUpdatedAt(),
                    scopeForTarget(context, item.getTargetJobId()),
                    includedStatus(item.getIncluded()),
                    includedReason(item.getIncluded(), item.getExcludeReason()),
                    item.getSourceHash(),
                    item.getSafeSummary(),
                    item.getMetadata()));
        }
        for (ApplicationEventItem item : safeList(evidence.getApplicationEvents())) {
            sources.add(source(
                    "JOB_APPLICATION_EVENT",
                    item.getEventId(),
                    item.getEventTime(),
                    item.getUpdatedAt(),
                    scopeForTarget(context, item.getTargetJobId()),
                    includedStatus(item.getIncluded()),
                    includedReason(item.getIncluded(), item.getExcludeReason()),
                    item.getSourceHash(),
                    item.getSafeSummary(),
                    item.getMetadata()));
        }
        for (CalendarEventItem item : safeList(evidence.getCalendarEvents())) {
            sources.add(source(
                    "CAREER_CALENDAR_EVENT",
                    item.getEventId(),
                    item.getStartsAtUtc(),
                    item.getUpdatedAt(),
                    scopeForTarget(context, item.getTargetJobId()),
                    includedStatus(item.getIncluded()),
                    includedReason(item.getIncluded(), item.getExcludeReason()),
                    item.getSourceHash(),
                    item.getSafeSummary(),
                    item.getMetadata()));
        }
        for (ExperimentItem item : safeList(evidence.getExperiments())) {
            sources.add(source(
                    "JOB_SEARCH_EXPERIMENT",
                    item.getExperimentId(),
                    item.getStartDate() == null ? null
                            : localDateTime(context, item.getStartDate()),
                    null,
                    context.getTargetScopeKey(),
                    includedStatus(item.getIncluded()),
                    includedReason(item.getIncluded(), item.getExcludeReason()),
                    item.getSourceHash(),
                    item.getSafeSummary(),
                    item.getMetadata()));
            for (ExperimentRelationItem relation : safeList(item.getRelations())) {
                sources.add(source(
                        "JOB_SEARCH_EXPERIMENT_RELATION",
                        relation.getRelationId(),
                        relation.getSourceTime(),
                        null,
                        scopeForTarget(context, relation.getTargetJobId()),
                        includedStatus(relation.getIncluded()),
                        includedReason(relation.getIncluded(), relation.getExcludeReason()),
                        relation.getSourceHash(),
                        "策略实验关联：类型="
                                + normalizeCode(relation.getRelationType(), "UNKNOWN"),
                        canonicalMap("relationType", safeCode(relation.getRelationType()))));
            }
        }
    }

    private void appendInterviewSources(
            RequestContext context,
            WeeklyInterviewEvidenceVO evidence,
            List<AgentWeeklyReportSource> sources) {
        if (evidence == null) {
            return;
        }
        for (SessionItem item : safeList(evidence.getSessions())) {
            sources.add(source(
                    "INTERVIEW_SESSION",
                    item.getSessionId(),
                    firstNonNull(item.getCompletedAt(), item.getStartedAt()),
                    item.getUpdatedAt(),
                    scopeForTarget(context, item.getTargetJobId()),
                    includedStatus(item.getIncluded()),
                    includedReason(item.getIncluded(), item.getExcludeReason()),
                    item.getSourceHash(),
                    item.getSafeSummary(),
                    item.getMetadata()));
        }
        for (ReportItem item : safeList(evidence.getReports())) {
            sources.add(source(
                    "INTERVIEW_REPORT",
                    item.getReportId(),
                    item.getGeneratedAt(),
                    item.getUpdatedAt(),
                    scopeForTarget(context, item.getTargetJobId()),
                    includedStatus(item.getIncluded()),
                    includedReason(item.getIncluded(), item.getExcludeReason()),
                    item.getSourceHash(),
                    item.getSafeSummary(),
                    item.getMetadata()));
        }
    }

    private void appendAvailabilitySources(
            RequestContext context,
            EvidenceBundle bundle,
            List<AgentWeeklyReportSource> sources) {
        if (!Boolean.TRUE.equals(bundle.getCareerAvailable())) {
            sources.add(source(
                    "WEEKLY_CAREER_EVIDENCE",
                    null,
                    null,
                    null,
                    context.getTargetScopeKey(),
                    "UNAVAILABLE",
                    firstText(bundle.getCareerFailureCode(), "SOURCE_UNAVAILABLE"),
                    localHash("WEEKLY_CAREER_EVIDENCE", bundle.getCareerFailureCode()),
                    "投递、日历和实验来源本次不可用",
                    Map.of()));
        }
        if (!Boolean.TRUE.equals(bundle.getInterviewAvailable())) {
            sources.add(source(
                    "WEEKLY_INTERVIEW_EVIDENCE",
                    null,
                    null,
                    null,
                    context.getTargetScopeKey(),
                    "UNAVAILABLE",
                    firstText(bundle.getInterviewFailureCode(), "SOURCE_UNAVAILABLE"),
                    localHash("WEEKLY_INTERVIEW_EVIDENCE", bundle.getInterviewFailureCode()),
                    "面试来源本次不可用",
                    Map.of()));
        }
        if (Boolean.TRUE.equals(bundle.getLocalTruncated())) {
            sources.add(source(
                    "WEEKLY_AGENT_EVIDENCE",
                    null,
                    null,
                    null,
                    context.getTargetScopeKey(),
                    "TRUNCATED",
                    "SOURCE_TRUNCATED",
                    localHash("WEEKLY_AGENT_EVIDENCE", "SOURCE_TRUNCATED"),
                    "Agent 本地来源达到查询上限",
                    Map.of()));
        }
        if (bundle.getCareerEvidence() != null
                && Boolean.TRUE.equals(bundle.getCareerEvidence().getTruncated())) {
            sources.add(source(
                    "WEEKLY_CAREER_EVIDENCE",
                    null,
                    null,
                    null,
                    context.getTargetScopeKey(),
                    "TRUNCATED",
                    "SOURCE_TRUNCATED",
                    localHash("WEEKLY_CAREER_EVIDENCE", "SOURCE_TRUNCATED"),
                    "投递、日历或实验来源达到查询上限",
                    Map.of()));
        }
        if (bundle.getInterviewEvidence() != null
                && Boolean.TRUE.equals(bundle.getInterviewEvidence().getTruncated())) {
            sources.add(source(
                    "WEEKLY_INTERVIEW_EVIDENCE",
                    null,
                    null,
                    null,
                    context.getTargetScopeKey(),
                    "TRUNCATED",
                    "SOURCE_TRUNCATED",
                    localHash("WEEKLY_INTERVIEW_EVIDENCE", "SOURCE_TRUNCATED"),
                    "面试来源达到查询上限",
                    Map.of()));
        }
    }

    private WeeklyReportCoverageVO buildCoverage(
            EvidenceBundle bundle,
            List<AgentWeeklyReportSource> sources) {
        WeeklyReportCoverageVO coverage = new WeeklyReportCoverageVO();
        for (AgentWeeklyReportSource source : sources) {
            Map<String, Integer> counts = switch (source.getInclusionStatus()) {
                case "INCLUDED" -> coverage.getIncludedCounts();
                case "EXCLUDED" -> coverage.getExcludedCounts();
                case "UNAVAILABLE" -> coverage.getUnavailableCounts();
                default -> null;
            };
            if (counts != null) {
                counts.merge(source.getSourceType(), 1, Integer::sum);
            }
            if ("TRUNCATED".equals(source.getInclusionStatus())) {
                coverage.setTruncated(true);
            }
        }
        mergeRemoteCounts(
                coverage,
                bundle.getCareerEvidence() == null
                        ? Map.of() : bundle.getCareerEvidence().getSourceCounts(),
                Map.of(
                        "applications", "JOB_APPLICATION",
                        "applicationEvents", "JOB_APPLICATION_EVENT",
                        "calendarEvents", "CAREER_CALENDAR_EVENT",
                        "experiments", "JOB_SEARCH_EXPERIMENT",
                        "experimentRelations", "JOB_SEARCH_EXPERIMENT_RELATION"));
        mergeRemoteCounts(
                coverage,
                bundle.getInterviewEvidence() == null
                        ? Map.of() : bundle.getInterviewEvidence().getSourceCounts(),
                Map.of(
                        "sessions", "INTERVIEW_SESSION",
                        "reports", "INTERVIEW_REPORT"));

        List<String> warnings = new ArrayList<>();
        for (String warning : safeList(bundle.getCollectionWarnings())) {
            addDistinct(warnings, humanWarning(warning));
        }
        if (bundle.getCareerEvidence() != null) {
            for (String warning : safeList(bundle.getCareerEvidence().getWarnings())) {
                addDistinct(warnings, humanWarning(warning));
            }
        }
        if (bundle.getInterviewEvidence() != null) {
            for (String warning : safeList(bundle.getInterviewEvidence().getWarnings())) {
                addDistinct(warnings, humanWarning(warning));
            }
        }
        if (!Boolean.TRUE.equals(bundle.getCareerAvailable())) {
            addDistinct(warnings, "投递、日历和策略实验来源本次不可用");
        }
        if (!Boolean.TRUE.equals(bundle.getInterviewAvailable())) {
            addDistinct(warnings, "面试来源本次不可用");
        }
        coverage.setWarnings(warnings);
        boolean remotePartial = bundle.getCareerEvidence() != null
                && !"COMPLETE".equalsIgnoreCase(bundle.getCareerEvidence().getConsistencyLevel())
                || bundle.getInterviewEvidence() != null
                && !"COMPLETE".equalsIgnoreCase(bundle.getInterviewEvidence().getConsistencyLevel());
        if (Boolean.TRUE.equals(coverage.getTruncated())) {
            coverage.setConsistencyLevel("BEST_EFFORT");
        } else if (!coverage.getUnavailableCounts().isEmpty()
                || !coverage.getExcludedCounts().isEmpty()
                || remotePartial) {
            coverage.setConsistencyLevel("PARTIAL");
        } else {
            coverage.setConsistencyLevel("COMPLETE");
        }
        return coverage;
    }

    private void mergeRemoteCounts(
            WeeklyReportCoverageVO coverage,
            Map<String, Integer> counts,
            Map<String, String> sourceTypes) {
        for (Map.Entry<String, String> mapping : sourceTypes.entrySet()) {
            int included = value(counts.get(mapping.getKey() + ".included"));
            int excluded = value(counts.get(mapping.getKey() + ".excluded"));
            int truncated = value(counts.get(mapping.getKey() + ".truncated"));
            putMax(coverage.getIncludedCounts(), mapping.getValue(), included);
            putMax(coverage.getExcludedCounts(), mapping.getValue(), excluded);
            if (truncated > 0) {
                coverage.setTruncated(true);
            }
        }
    }

    private Object inputFingerprint(
            RequestContext context,
            List<AgentWeeklyReportSource> sources,
            WeeklyReportCoverageVO coverage,
            List<WeeklyReportFactVO> facts,
            List<WeeklyReportSignalVO> signals,
            List<WeeklyExperimentSuggestionVO> suggestions,
            List<String> limits) {
        List<String> sourceSignatures = sources.stream()
                .map(source -> String.join("|",
                        nullText(source.getSourceType()),
                        nullText(source.getSourceId()),
                        nullText(source.getInclusionStatus()),
                        nullText(source.getExcludeReason()),
                        nullText(source.getSourceHash())))
                .sorted()
                .toList();
        return canonicalMap(
                "weekStartDate", context.getWeekStartDate(),
                "weekEndDate", context.getWeekEndDate(),
                "targetScopeKey", context.getTargetScopeKey(),
                "timezone", context.getTimezone(),
                "reportStatus", context.getReportStatus(),
                "sourceSignatures", sourceSignatures,
                "includedCounts", coverage.getIncludedCounts(),
                "excludedCounts", coverage.getExcludedCounts(),
                "unavailableCounts", coverage.getUnavailableCounts(),
                "truncated", coverage.getTruncated(),
                "consistencyLevel", coverage.getConsistencyLevel(),
                "facts", facts,
                "signals", signals,
                "suggestions", suggestions,
                "limits", limits);
    }

    private AgentWeeklyReportSource source(
            String sourceType,
            Long sourceId,
            LocalDateTime sourceTime,
            LocalDateTime sourceUpdatedAt,
            String scopeKey,
            String inclusionStatus,
            String excludeReason,
            String sourceHash,
            String safeSummary,
            Map<String, Object> metadata) {
        AgentWeeklyReportSource source = new AgentWeeklyReportSource();
        source.setSourceType(sourceType);
        source.setSourceId(sourceId);
        source.setSourceTime(sourceTime);
        source.setSourceUpdatedAt(sourceUpdatedAt);
        source.setScopeKey(scopeKey);
        source.setInclusionStatus(inclusionStatus);
        source.setExcludeReason(excludeReason);
        source.setSourceHash(StringUtils.hasText(sourceHash)
                ? sourceHash : localHash(sourceType, sourceId, sourceTime, sourceUpdatedAt));
        source.setSafeSummary(sanitizer.safeText(safeSummary, 240));
        source.setMetadataJson(jsonCodec.toJson(metadata == null ? Map.of() : metadata));
        return source;
    }

    private WeeklyExperimentSuggestionVO suggestion(
            RequestContext context,
            String key,
            String title,
            String hypothesis,
            String primaryVariable,
            List<String> fixedVariables,
            String expectedSignal,
            String successMetric,
            int minimumSample,
            int observationDays,
            String stopCondition,
            String confidenceLevel,
            List<String> basedOnSignalIds,
            List<String> sourceRefs) {
        WeeklyExperimentSuggestionVO suggestion = new WeeklyExperimentSuggestionVO();
        String scopeToken = context.getTargetJobId() == null
                ? "ALL" : "JOB-" + context.getTargetJobId();
        suggestion.setSuggestionId(
                "weekly:" + context.getWeekStartDate() + ":" + scopeToken + ":" + key);
        suggestion.setSemanticKey("sha256:" + hashUtils.hash(canonicalMap(
                "weekStartDate", context.getWeekStartDate(),
                "targetScopeKey", context.getTargetScopeKey(),
                "key", key,
                "primaryVariable", primaryVariable,
                "fixedVariables", fixedVariables)));
        suggestion.setTitle(title);
        suggestion.setHypothesis(hypothesis);
        suggestion.setPrimaryVariable(primaryVariable);
        suggestion.setFixedVariables(new ArrayList<>(fixedVariables));
        suggestion.setExpectedSignal(expectedSignal);
        suggestion.setSuccessMetric(successMetric);
        suggestion.setTargetSample(minimumSample);
        suggestion.setMinimumSample(minimumSample);
        suggestion.setObservationDays(observationDays);
        suggestion.setStopCondition(stopCondition);
        suggestion.setConfidenceLevel(confidenceLevel);
        suggestion.setBasedOnSignalIds(new ArrayList<>(basedOnSignalIds));
        suggestion.setSourceRefs(new ArrayList<>(sourceRefs));
        suggestion.setStatus("TO_VALIDATE");
        return suggestion;
    }

    private WeeklyReportHypothesisVO toHypothesis(WeeklyExperimentSuggestionVO source) {
        WeeklyReportHypothesisVO target = new WeeklyReportHypothesisVO();
        target.setHypothesisId(source.getSuggestionId());
        target.setStatement(source.getHypothesis());
        target.setPrimaryVariable(source.getPrimaryVariable());
        target.setFixedVariables(new ArrayList<>(source.getFixedVariables()));
        target.setExpectedSignal(source.getExpectedSignal());
        target.setSuccessMetric(source.getSuccessMetric());
        target.setMinimumSample(source.getMinimumSample());
        target.setObservationDays(source.getObservationDays());
        target.setStopCondition(source.getStopCondition());
        target.setConfidenceLevel(source.getConfidenceLevel());
        target.setBasedOnSignalIds(new ArrayList<>(source.getBasedOnSignalIds()));
        target.setSourceRefs(new ArrayList<>(source.getSourceRefs()));
        target.setStatus("TO_VALIDATE");
        return target;
    }

    private WeeklyPlanDraftVO buildPlanDraft(
            RequestContext context,
            List<WeeklyExperimentSuggestionVO> suggestions) {
        WeeklyPlanDraftVO draft = new WeeklyPlanDraftVO();
        draft.setAvailable(false);
        draft.setTargetWeekStart(context.getWeekStartDate().plusDays(7).toString());
        draft.setUnavailableReason("当前阶段五仅支持每日复盘建议预览，周报行动暂以只读方式展示");
        List<WeeklyPlanDraftItemVO> items = new ArrayList<>();
        for (WeeklyExperimentSuggestionVO suggestion : suggestions) {
            WeeklyPlanDraftItemVO item = new WeeklyPlanDraftItemVO();
            item.setSemanticKey(suggestion.getSemanticKey());
            item.setTargetDate(context.getWeekStartDate().plusDays(8));
            item.setActionType(actionType(suggestion.getPrimaryVariable()));
            item.setTitle(suggestion.getTitle());
            item.setDescription(suggestion.getHypothesis());
            item.setReason("来自本周求职周报的待验证建议");
            item.setSourceHypothesisId(suggestion.getSuggestionId());
            item.setEstimatedMinutes(30);
            item.setPriority("MEDIUM");
            item.setConflictCheckRequired(true);
            item.setRequiresUserConfirmation(true);
            item.setUserDecision("PENDING");
            items.add(item);
        }
        draft.setItems(items);
        return draft;
    }

    private WeeklyReportSignalVO signal(
            String id,
            String type,
            String direction,
            String title,
            String description,
            String confidence,
            String scope,
            List<String> blockedConclusions) {
        WeeklyReportSignalVO signal = new WeeklyReportSignalVO();
        signal.setSignalId(id);
        signal.setSignalType(type);
        signal.setDirection(direction);
        signal.setTitle(title);
        signal.setDescription(description);
        signal.setConfidenceLevel(confidence);
        signal.setScope(scope);
        signal.setBlockedConclusions(new ArrayList<>(safeList(blockedConclusions)));
        return signal;
    }

    private void addFact(
            List<WeeklyReportFactVO> facts,
            String id,
            String type,
            String label,
            Object value,
            String unit,
            String scope,
            String timeWindow,
            List<String> sourceRefs) {
        WeeklyReportFactVO fact = new WeeklyReportFactVO();
        fact.setFactId(id);
        fact.setFactType(type);
        fact.setLabel(label);
        fact.setValue(value);
        fact.setUnit(unit);
        fact.setScope(scope);
        fact.setTimeWindow(timeWindow);
        fact.setSourceRefs(sourceRefs);
        fact.setCalculationVersion(WeeklyReportVersions.CALCULATION_VERSION);
        facts.add(fact);
    }

    private void updateSegment(
            Map<String, WeeklyReportSamplePolicy.SegmentMetric> segments,
            String key,
            Long applicationId,
            MetricState state) {
        WeeklyReportSamplePolicy.SegmentMetric metric =
                segments.computeIfAbsent(key, ignored -> new WeeklyReportSamplePolicy.SegmentMetric());
        metric.setActivityCount(value(metric.getActivityCount()) + 1);
        if (applicationId != null && state.maturedApplicationIds.contains(applicationId)) {
            metric.setMaturedCount(value(metric.getMaturedCount()) + 1);
        }
        if (applicationId != null && state.verifiedResponseApplicationIds.contains(applicationId)) {
            metric.setVerifiedResponseCount(value(metric.getVerifiedResponseCount()) + 1);
        }
        state.segmentApplicationIds.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                .add(applicationId);
    }

    private Map<String, WeeklyReportSamplePolicy.SegmentMetric> toPolicySegments(
            Map<String, WeeklyReportSamplePolicy.SegmentMetric> source) {
        return source;
    }

    private Map<String, Object> segmentRates(
            Map<String, WeeklyReportSamplePolicy.SegmentMetric> segments) {
        Map<String, Object> result = new LinkedHashMap<>();
        new TreeMap<>(segments).forEach((key, metric) -> result.put(
                key, percent(value(metric.getVerifiedResponseCount()), value(metric.getMaturedCount()))));
        return result;
    }

    private List<Map<String, Object>> eligibleSegments(
            Map<String, WeeklyReportSamplePolicy.SegmentMetric> segments,
            String keyName,
            int minimumMatured) {
        List<Map<String, Object>> result = new ArrayList<>();
        new TreeMap<>(segments).forEach((key, metric) -> result.add(canonicalMap(
                keyName, key,
                "minimumMaturedApplications", minimumMatured,
                "currentMaturedApplications", value(metric.getMaturedCount()))));
        return result;
    }

    private List<String> segmentRefs(
            MetricState state,
            Map<String, WeeklyReportSamplePolicy.SegmentMetric> segments,
            RefLimiter limiter) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        segments.keySet().stream().sorted().forEach(key ->
                ids.addAll(safeSet(state.segmentApplicationIds.get(key))));
        return refs("JOB_APPLICATION", ids, limiter);
    }

    private List<String> segmentRefsWithoutLimit(
            MetricState state,
            Map<String, WeeklyReportSamplePolicy.SegmentMetric> segments) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        segments.keySet().stream().sorted().forEach(key ->
                ids.addAll(safeSet(state.segmentApplicationIds.get(key))));
        return refsWithoutLimit("JOB_APPLICATION", ids);
    }

    private List<String> refsForPlanStatus(
            List<AgentWeekPlanItem> items,
            Set<String> statuses,
            RefLimiter limiter) {
        return refs(
                "AGENT_WEEK_PLAN_ITEM",
                ids(items.stream()
                                .filter(item -> statuses.contains(
                                        normalizeCode(item.getItemStatus(), "TODO")))
                                .toList(),
                        AgentWeekPlanItem::getId),
                limiter);
    }

    private List<String> eventRefs(
            List<ApplicationEventItem> events,
            Set<String> eventTypes,
            RefLimiter limiter) {
        return refs(
                "JOB_APPLICATION_EVENT",
                ids(events.stream()
                                .filter(item -> eventTypes.contains(
                                        normalizeCode(item.getEventType(), "UNKNOWN")))
                                .toList(),
                        ApplicationEventItem::getEventId),
                limiter);
    }

    private List<String> calendarRefs(
            List<CalendarEventItem> events,
            boolean cancelled,
            RefLimiter limiter) {
        return refs(
                "CAREER_CALENDAR_EVENT",
                ids(events.stream()
                                .filter(item -> cancelled == (
                                        "CANCELLED_CALENDAR_EVENT".equals(
                                                normalizeCode(item.getEventType(), "OTHER"))
                                                || "CANCELLED".equalsIgnoreCase(item.getStatus())))
                                .toList(),
                        CalendarEventItem::getEventId),
                limiter);
    }

    private List<String> maturedRefs(MetricState state, RefLimiter limiter) {
        List<String> applicationRefs = refs(
                "JOB_APPLICATION",
                state.applications.stream()
                        .map(ApplicationItem::getApplicationId)
                        .filter(state.maturedApplicationIds::contains)
                        .toList(),
                limiter);
        List<String> eventRefs = eventRefs(state.applicationEvents, MATURITY_EVENT_TYPES, limiter);
        return combineRefs(applicationRefs, eventRefs, limiter);
    }

    private List<String> experimentRelationRefs(
            List<ExperimentItem> experiments,
            RefLimiter limiter) {
        List<Long> ids = experiments.stream()
                .flatMap(item -> safeList(item.getRelations()).stream())
                .filter(Objects::nonNull)
                .filter(item -> Boolean.TRUE.equals(item.getIncluded()))
                .map(ExperimentRelationItem::getRelationId)
                .filter(Objects::nonNull)
                .toList();
        return refs("JOB_SEARCH_EXPERIMENT_RELATION", ids, limiter);
    }

    private List<String> combineRefs(
            List<String> left,
            List<String> right,
            RefLimiter limiter) {
        LinkedHashSet<String> combined = new LinkedHashSet<>();
        combined.addAll(safeList(left));
        combined.addAll(safeList(right));
        if (combined.size() > MAX_SOURCE_REFS) {
            limiter.truncated = true;
        }
        return combined.stream().sorted().limit(MAX_SOURCE_REFS).toList();
    }

    private List<String> refs(
            String sourceType,
            Collection<Long> ids,
            RefLimiter limiter) {
        List<String> result = refsWithoutLimit(sourceType, ids);
        if (result.size() > MAX_SOURCE_REFS) {
            limiter.truncated = true;
        }
        return result.stream().limit(MAX_SOURCE_REFS).toList();
    }

    private List<String> refsWithoutLimit(String sourceType, Collection<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(id -> sourceType + ":" + id)
                .toList();
    }

    private <T> List<Long> ids(List<T> values, Function<T, Long> extractor) {
        return safeList(values).stream()
                .filter(Objects::nonNull)
                .map(extractor)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private <T> List<T> distinctIncluded(
            List<T> values,
            Function<T, Long> idExtractor,
            Function<T, Boolean> includedExtractor) {
        LinkedHashMap<Long, T> result = new LinkedHashMap<>();
        int syntheticId = 0;
        for (T value : safeList(values)) {
            if (value == null || !Boolean.TRUE.equals(includedExtractor.apply(value))) {
                continue;
            }
            Long id = idExtractor.apply(value);
            result.putIfAbsent(id == null ? Long.MIN_VALUE + syntheticId++ : id, value);
        }
        return new ArrayList<>(result.values());
    }

    private int trustedReportGroupMaximum(List<ReportItem> reports) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ReportItem report : reports) {
            String key = report.getTargetJobId() + "|"
                    + nullText(report.getRubricVersion()) + "|"
                    + nullText(report.getDimensionFingerprint());
            counts.merge(key, 1, Integer::sum);
        }
        return counts.values().stream().max(Integer::compareTo).orElse(0);
    }

    private Integer historicalNumber(AgentWeeklyReportSnapshot snapshot, String factId) {
        if (snapshot == null) {
            return null;
        }
        List<WeeklyReportFactVO> facts = jsonCodec.fromJson(
                snapshot.getFactsJson(),
                new TypeReference<List<WeeklyReportFactVO>>() { },
                List.of());
        for (WeeklyReportFactVO fact : facts) {
            if (fact != null && factId.equals(fact.getFactId()) && fact.getValue() != null) {
                Object value = fact.getValue();
                if (value instanceof Number number) {
                    return number.intValue();
                }
                try {
                    return Integer.valueOf(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private BigDecimal percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private String actionType(String primaryVariable) {
        return switch (normalizeCode(primaryVariable, "UNKNOWN")) {
            case "CHANNEL", "APPLICATION_TIMING", "TARGET_JOB" -> "COLLECT_APPLICATION_SAMPLE";
            case "RESUME_VERSION" -> "COLLECT_RESUME_VERSION_SAMPLE";
            case "TRAINING_TOPIC", "INTERVIEW_TOPIC" -> "INTERVIEW_PRACTICE";
            case "PROJECT_EVIDENCE" -> "ADD_PROJECT_EVIDENCE";
            default -> "COMPLETE_REVIEW";
        };
    }

    private String direction(int current, int baseline) {
        return current > baseline ? "UP" : current < baseline ? "DOWN" : "STABLE";
    }

    private String normalizeDirection(String value) {
        String normalized = normalizeCode(value, "MIXED");
        return Set.of("UP", "DOWN", "STABLE", "MIXED").contains(normalized)
                ? normalized : "MIXED";
    }

    private String includedStatus(Boolean included) {
        return Boolean.TRUE.equals(included) ? "INCLUDED" : "EXCLUDED";
    }

    private String includedReason(Boolean included, String reason) {
        return Boolean.TRUE.equals(included)
                ? null : firstText(reason, "SOURCE_NOT_INCLUDED");
    }

    private String scopeForTarget(RequestContext context, Long targetJobId) {
        if (targetJobId != null) {
            return "TARGET_JOB:" + targetJobId;
        }
        return context.getTargetScopeKey();
    }

    private LocalDateTime applicationBusinessTime(ApplicationItem item) {
        return item == null ? null
                : firstNonNull(item.getAppliedAt(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private LocalDateTime localDateTime(RequestContext context, LocalDate date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(
                date.atStartOfDay(context.getZoneId()).toInstant(), ZoneOffset.UTC);
    }

    private String localHash(String type, Object... values) {
        List<Object> canonical = new ArrayList<>();
        canonical.add(type);
        Collections.addAll(canonical, values);
        return "sha256:" + hashUtils.hash(canonical);
    }

    private String humanWarning(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("TRUNCATED")) {
            return "部分来源达到查询上限，本次仅使用已返回记录";
        }
        if (normalized.contains("EXCLUDED")) {
            return "部分记录因时间、范围、归属或可信条件不满足而未纳入周报";
        }
        if (normalized.contains("REDACTED")) {
            return "部分来源关联信息无法安全确认，已隐藏并降低可信度";
        }
        if (normalized.contains("NORMALIZATION")) {
            return "部分面试报告经过兼容归一化，仅在口径一致时参与比较";
        }
        return "部分来源存在质量限制，周报已按可信边界处理";
    }

    private String confidenceWarning(String confidenceLevel) {
        return switch (firstText(confidenceLevel, "FACT_ONLY")) {
            case "MEDIUM" -> "当前结论为中等可信，只用于方向观察";
            case "LOW" -> "当前样本或来源不足，只用于弱观察";
            default -> "当前仅展示事实，不形成策略优劣结论";
        };
    }

    private String safeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z0-9_.:-]{1,128}") ? normalized : "OTHER";
    }

    private String normalizeCode(String value, String fallback) {
        return sanitizer.normalizeCode(value, fallback);
    }

    private void putMax(Map<String, Integer> values, String key, int candidate) {
        if (candidate > 0) {
            values.merge(key, candidate, Math::max);
        }
    }

    private void addDistinct(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private Map<String, Object> canonicalMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String nullText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> Set<T> safeSet(Set<T> values) {
        return values == null ? Set.of() : values;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static final class Selection<T> {

        private final List<T> items = new ArrayList<>();
        private final Set<T> included = Collections.newSetFromMap(new IdentityHashMap<>());
        private final IdentityHashMap<T, String> reasons = new IdentityHashMap<>();

        private void include(T value) {
            items.add(value);
            included.add(value);
        }

        private void exclude(T value, String reason) {
            reasons.put(value, reason);
        }

        private List<T> items() {
            return items;
        }

        private boolean isIncluded(T value) {
            return included.contains(value);
        }

        private String reason(T value) {
            return reasons.getOrDefault(value, "SOURCE_NOT_INCLUDED");
        }
    }

    private static final class RefLimiter {

        private boolean truncated;
    }

    private static final class MetricState {

        private List<AgentWeekPlanItem> planItems = List.of();
        private List<AgentReview> dailyReviews = List.of();
        private List<AgentPlanAdjustment> adjustments = List.of();
        private List<AgentPlanInfluence> influences = List.of();
        private List<ReadinessScoreRecord> readinessRecords = List.of();
        private List<SkillGrowthSnapshot> skillSnapshots = List.of();
        private List<AgentWeeklyReportSnapshot> comparableSnapshots = List.of();
        private List<ApplicationItem> applications = List.of();
        private List<ApplicationEventItem> applicationEvents = List.of();
        private List<CalendarEventItem> calendarEvents = List.of();
        private List<ExperimentItem> experiments = List.of();
        private List<SessionItem> interviewSessions = List.of();
        private List<ReportItem> interviewReports = List.of();
        private List<ComparisonGroupItem> comparisonGroups = List.of();
        private final Set<Long> maturedApplicationIds = new LinkedHashSet<>();
        private final Set<Long> verifiedResponseApplicationIds = new LinkedHashSet<>();
        private final Set<Long> followUpApplicationIds = new LinkedHashSet<>();
        private final Map<String, WeeklyReportSamplePolicy.SegmentMetric> channelSegments =
                new LinkedHashMap<>();
        private final Map<String, WeeklyReportSamplePolicy.SegmentMetric> resumeVersionSegments =
                new LinkedHashMap<>();
        private final Map<String, Set<Long>> segmentApplicationIds = new LinkedHashMap<>();
        private int planDoneCount;
        private int planSkippedCount;
        private int planDeferredCount;
        private int planTodoCount;
        private int dailyDoneCount;
        private int dailySkippedCount;
        private int dailyTodoCount;
        private int dailyTaskCount;
        private int immatureApplicationCount;
        private int realInterviewInviteCount;
        private int realInterviewCompletedCount;
        private int calendarPlannedCount;
        private int calendarCancelledCount;
        private int calendarUnlinkedCount;
        private int includedExperimentRelationCount;
        private int trustedComparableInterviewCount;
        private int comparableWeekCount;
        private Integer latestReadinessScore;
        private Integer previousReadinessScore;
        private Integer previousApplicationActivityCount;
    }
}
