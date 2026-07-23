package com.codecoachai.interview.service.impl;

import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.enums.InterviewModeEnum;
import com.codecoachai.interview.domain.enums.InterviewStatusEnum;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.domain.vo.WeeklyInterviewEvidenceVO;
import com.codecoachai.interview.domain.vo.WeeklyInterviewEvidenceVO.ComparisonGroupItem;
import com.codecoachai.interview.domain.vo.WeeklyInterviewEvidenceVO.ReportItem;
import com.codecoachai.interview.domain.vo.WeeklyInterviewEvidenceVO.SessionItem;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.service.WeeklyInterviewEvidenceService;
import com.codecoachai.interview.support.InterviewReportComparabilityPolicy;
import com.codecoachai.interview.support.InterviewReportScoringContract;
import com.codecoachai.interview.support.InterviewReportTrustPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WeeklyInterviewEvidenceServiceImpl implements WeeklyInterviewEvidenceService {

    static final int MAX_SESSIONS = 100;
    static final int MAX_REPORTS = 100;

    private static final int MIN_TREND_REPORTS = 3;
    private static final int MAX_WEAKNESSES_PER_REPORT = 8;
    private static final Pattern SAFE_CODE_PATTERN =
            Pattern.compile("[A-Z][A-Z0-9_.:-]{0,127}");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(?i).*[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}.*");
    private static final Pattern LONG_DIGIT_PATTERN = Pattern.compile(".*\\d{8,}.*");
    private static final Set<String> INTERVIEW_MODES = enumNames(InterviewModeEnum.class);
    private static final Set<String> INTERVIEW_STATUSES = enumNames(InterviewStatusEnum.class);
    private static final Set<String> REPORT_STATUSES = enumNames(ReportStatusEnum.class);
    private static final List<String> WEAKNESS_JSON_FIELDS = List.of(
            "name",
            "skillName",
            "knowledgePoint",
            "dimension",
            "dimensionCode",
            "code",
            "label");

    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final ObjectMapper objectMapper;
    private final InterviewReportComparabilityPolicy comparabilityPolicy;

    @Override
    public WeeklyInterviewEvidenceVO getWeeklyEvidence(
            Long userId,
            LocalDateTime rangeStartUtc,
            LocalDateTime rangeEndUtc,
            LocalDateTime sourceCutoffAt,
            Long targetJobId,
            String timezone) {
        RequestWindow window = validateRequest(
                userId,
                rangeStartUtc,
                rangeEndUtc,
                sourceCutoffAt,
                targetJobId,
                timezone);
        CollectionState state = new CollectionState();

        List<InterviewReport> reportCandidates = trim(
                sortReports(reportMapper.selectWeeklyEvidenceReports(
                        window.userId(),
                        window.rangeStartUtc(),
                        window.rangeEndUtc(),
                        window.sourceCutoffAt(),
                        window.targetJobId(),
                        MAX_REPORTS + 1)),
                MAX_REPORTS,
                "reports",
                "REPORTS_TRUNCATED",
                state);
        List<InterviewSession> completedSessionCandidates = trim(
                sortSessions(sessionMapper.selectWeeklyEvidenceSessions(
                        window.userId(),
                        window.rangeStartUtc(),
                        window.rangeEndUtc(),
                        window.sourceCutoffAt(),
                        window.targetJobId(),
                        MAX_SESSIONS + 1)),
                MAX_SESSIONS,
                "sessions",
                "SESSIONS_TRUNCATED",
                state);

        List<Long> reportSessionIds = reportCandidates.stream()
                .filter(Objects::nonNull)
                .map(InterviewReport::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<InterviewSession> linkedSessionCandidates = reportSessionIds.isEmpty()
                ? List.of()
                : safeList(sessionMapper.selectWeeklyEvidenceSessionsByIds(
                        window.userId(),
                        reportSessionIds,
                        window.sourceCutoffAt(),
                        window.targetJobId()));

        SessionCollection sessions = collectSessions(
                window,
                completedSessionCandidates,
                linkedSessionCandidates,
                state);
        ReportCollection reports = collectReports(
                window,
                reportCandidates,
                sessions.sessionsById(),
                state);
        List<SessionItem> sessionItems = buildSessionItems(
                sessions,
                reports.activeSessionIds(),
                reports.firstReportActivityBySession(),
                state);
        List<ComparisonGroupItem> comparisonGroups =
                buildComparisonGroups(reports.evaluatedReports());

        WeeklyInterviewEvidenceVO result = new WeeklyInterviewEvidenceVO();
        result.setUserId(window.userId());
        result.setSessions(sessionItems);
        result.setReports(reports.reportItems());
        result.setComparisonGroups(comparisonGroups);
        fillSourceCounts(result, sessionItems, reports.reportItems(), comparisonGroups, state);
        result.setTruncated(state.truncated);
        result.setWarnings(new ArrayList<>(state.warnings));
        result.setConsistencyLevel(state.truncated
                ? "BEST_EFFORT"
                : state.partial ? "PARTIAL" : "COMPLETE");
        return result;
    }

    private RequestWindow validateRequest(
            Long userId,
            LocalDateTime rangeStartUtc,
            LocalDateTime rangeEndUtc,
            LocalDateTime sourceCutoffAt,
            Long targetJobId,
            String timezone) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId 必须为正数");
        }
        if (rangeStartUtc == null || rangeEndUtc == null || sourceCutoffAt == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "rangeStartUtc、rangeEndUtc 和 sourceCutoffAt 不能为空");
        }
        if (!rangeStartUtc.isBefore(rangeEndUtc)) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "rangeStartUtc 必须早于 rangeEndUtc");
        }
        if (sourceCutoffAt.isBefore(rangeStartUtc)) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "sourceCutoffAt 不能早于 rangeStartUtc");
        }
        if (targetJobId != null && targetJobId <= 0) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "targetJobId 必须为正数");
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException ex) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "timezone 必须是有效的 ZoneId");
        }
        return new RequestWindow(
                userId,
                rangeStartUtc,
                rangeEndUtc,
                sourceCutoffAt,
                targetJobId);
    }

    private SessionCollection collectSessions(
            RequestWindow window,
            List<InterviewSession> completedCandidates,
            List<InterviewSession> linkedCandidates,
            CollectionState state) {
        Map<Long, InterviewSession> sessionsById = new LinkedHashMap<>();
        Set<Long> completedSessionIds = new LinkedHashSet<>();

        for (InterviewSession session : safeList(completedCandidates)) {
            if (!ownedVisibleSession(session, window)) {
                state.drop("sessions", "SESSION_RECORDS_EXCLUDED");
                continue;
            }
            if (!inWindowAtCutoff(session.getEndTime(), window)) {
                state.drop("sessions", "SESSION_RECORDS_EXCLUDED");
                continue;
            }
            sessionsById.putIfAbsent(session.getId(), session);
            completedSessionIds.add(session.getId());
        }
        for (InterviewSession session : safeList(linkedCandidates)) {
            if (!ownedVisibleSession(session, window)) {
                state.drop("sessions", "SESSION_RECORDS_EXCLUDED");
                continue;
            }
            sessionsById.putIfAbsent(session.getId(), session);
        }
        return new SessionCollection(sessionsById, completedSessionIds);
    }

    private ReportCollection collectReports(
            RequestWindow window,
            List<InterviewReport> reportCandidates,
            Map<Long, InterviewSession> sessionsById,
            CollectionState state) {
        List<ReportItem> reportItems = new ArrayList<>();
        List<EvaluatedReport> evaluatedReports = new ArrayList<>();
        Set<Long> activeSessionIds = new LinkedHashSet<>();
        Map<Long, LocalDateTime> firstReportActivityBySession = new HashMap<>();

        for (InterviewReport report : safeList(reportCandidates)) {
            if (!ownedVisibleReport(report, window)) {
                state.drop("reports", "REPORT_RECORDS_EXCLUDED");
                continue;
            }
            InterviewSession session = sessionsById.get(report.getSessionId());
            if (session == null || !ownedVisibleSession(session, window)) {
                state.drop("reports", "REPORT_SESSION_UNAVAILABLE");
                continue;
            }
            if (!reportActivityInWindow(report, session, window)) {
                state.drop("reports", "REPORT_RECORDS_EXCLUDED");
                continue;
            }

            InterviewReportComparabilityPolicy.Result comparison =
                    comparabilityPolicy.evaluate(report, session);
            InterviewReportComparabilityPolicy.Identity identity =
                    comparison.comparable()
                            ? identityFromComparable(comparison)
                            : comparabilityPolicy.identify(report, session);
            ReportItem item = toReportItem(report, session, comparison, identity, state);
            reportItems.add(item);
            evaluatedReports.add(new EvaluatedReport(report, item, comparison, identity));
            activeSessionIds.add(session.getId());
            firstReportActivityBySession.merge(
                    session.getId(),
                    reportBusinessTime(report),
                    this::earlierNonNull);
        }
        return new ReportCollection(
                reportItems,
                evaluatedReports,
                activeSessionIds,
                firstReportActivityBySession);
    }

    private List<SessionItem> buildSessionItems(
            SessionCollection sessions,
            Set<Long> activeReportSessionIds,
            Map<Long, LocalDateTime> firstReportActivityBySession,
            CollectionState state) {
        List<InterviewSession> reportSessions = activeReportSessionIds.stream()
                .map(sessions.sessionsById()::get)
                .filter(Objects::nonNull)
                .sorted(sessionComparator(firstReportActivityBySession))
                .toList();
        List<InterviewSession> completedOnlySessions = sessions.completedSessionIds().stream()
                .filter(sessionId -> !activeReportSessionIds.contains(sessionId))
                .map(sessions.sessionsById()::get)
                .filter(Objects::nonNull)
                .sorted(sessionComparator(firstReportActivityBySession))
                .toList();

        List<InterviewSession> selected = new ArrayList<>(reportSessions);
        selected.addAll(completedOnlySessions);
        selected = trim(
                selected,
                MAX_SESSIONS,
                "sessions",
                "SESSIONS_TRUNCATED",
                state);
        selected.sort(sessionComparator(firstReportActivityBySession));

        List<SessionItem> items = new ArrayList<>();
        for (InterviewSession session : selected) {
            boolean completedInWindow = sessions.completedSessionIds().contains(session.getId());
            boolean reportActivity = activeReportSessionIds.contains(session.getId());
            items.add(toSessionItem(session, completedInWindow, reportActivity));
        }
        return items;
    }

    private SessionItem toSessionItem(
            InterviewSession session,
            boolean completedInWindow,
            boolean reportActivity) {
        SessionItem item = new SessionItem();
        item.setSessionId(session.getId());
        item.setTargetJobId(session.getTargetJobId());
        item.setApplicationId(session.getApplicationId());
        item.setMode(normalizeCode(session.getMode(), INTERVIEW_MODES));
        item.setStatus(normalizeCode(session.getStatus(), INTERVIEW_STATUSES));
        item.setStartedAt(session.getStartTime());
        item.setCompletedAt(session.getEndTime());
        item.setUpdatedAt(session.getUpdatedAt());
        item.setIncluded(true);
        item.setSourceHash(sourceHash(
                "INTERVIEW_SESSION",
                session.getId(),
                session.getUserId(),
                session.getTargetJobId(),
                session.getApplicationId(),
                session.getMode(),
                session.getStatus(),
                session.getReportStatus(),
                session.getStartTime(),
                session.getEndTime(),
                session.getCreatedAt(),
                session.getUpdatedAt()));
        item.setSafeSummary(buildSessionSummary(item));
        List<String> activitySources = new ArrayList<>();
        if (completedInWindow) {
            activitySources.add("SESSION_COMPLETED");
        }
        if (reportActivity) {
            activitySources.add("REPORT_ACTIVITY");
        }
        item.getMetadata().put("activitySources", activitySources);
        item.getMetadata().put(
                "reportStatus",
                normalizeCode(session.getReportStatus(), REPORT_STATUSES));
        return item;
    }

    private ReportItem toReportItem(
            InterviewReport report,
            InterviewSession session,
            InterviewReportComparabilityPolicy.Result comparison,
            InterviewReportComparabilityPolicy.Identity identity,
            CollectionState state) {
        boolean sampleInsufficient = InterviewReportTrustPolicy.isSampleInsufficient(report);
        boolean fallback = InterviewReportTrustPolicy.hasFallbackEvidence(report);
        boolean formalActionTrusted =
                InterviewReportTrustPolicy.isTrustedForFormalAction(report);
        InterviewReportScoringContract.Validation scoring =
                InterviewReportScoringContract.validate(
                        objectMapper,
                        report.getTotalScore(),
                        report.getRubricVersion(),
                        report.getRubricScores());

        boolean included = comparison.comparable();
        String trustStatus = trustStatus(
                report,
                comparison,
                scoring,
                formalActionTrusted,
                sampleInsufficient,
                fallback);
        String exclusionReason = included
                ? null
                : exclusionReason(
                        comparison,
                        scoring,
                        sampleInsufficient,
                        fallback);
        if (!included) {
            state.partial("REPORT_RECORDS_EXCLUDED");
        }
        comparison.normalizationWarnings().forEach(notice ->
                state.warn("REPORT_NORMALIZATION_" + notice.code()));

        String rubricVersion = safeRubricVersion(identity.rubricVersion());
        List<String> weaknesses = normalizeWeaknesses(report);
        Integer normalizedScore = comparison.totalScore();

        ReportItem item = new ReportItem();
        item.setReportId(report.getId());
        item.setSessionId(report.getSessionId());
        item.setTargetJobId(session.getTargetJobId());
        item.setApplicationId(session.getApplicationId());
        item.setReportStatus(normalizeCode(report.getStatus(), REPORT_STATUSES));
        item.setTrustStatus(trustStatus);
        item.setFallback(fallback);
        item.setSampleInsufficient(sampleInsufficient);
        item.setTotalScore(validScore(normalizedScore)
                ? BigDecimal.valueOf(normalizedScore) : null);
        item.setRubricVersion(rubricVersion);
        item.setDimensionFingerprint(identity.dimensionFingerprint());
        item.setGeneratedAt(report.getGeneratedAt());
        item.setUpdatedAt(report.getUpdatedAt());
        item.setIncluded(included);
        item.setExcludeReason(exclusionReason);
        item.setNormalizedWeaknesses(weaknesses);
        item.setSourceHash(sourceHash(
                "INTERVIEW_REPORT",
                report.getId(),
                report.getSessionId(),
                report.getUserId(),
                report.getStatus(),
                report.getTotalScore(),
                report.getRubricVersion(),
                report.getRubricScores(),
                report.getWeakPoints(),
                report.getWeaknesses(),
                report.getMainProblems(),
                report.getSummary(),
                report.getReportContent(),
                report.getAdviceEvidence(),
                report.getQaReview(),
                report.getAbilityProfileUpdates(),
                report.getGeneratedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt()));
        item.setSafeSummary(buildReportSummary(item));
        item.getMetadata().put("formalActionTrusted", formalActionTrusted);
        item.getMetadata().put("scoringContractValid", scoring.valid());
        putIfText(item.getMetadata(), "scoringContractReasonCode", scoring.reasonCode());
        putIfText(item.getMetadata(), "normalizationSource", comparison.normalizationSource());
        putIfText(item.getMetadata(), "comparisonReasonCode", comparison.reasonCode());
        item.getMetadata().put(
                "normalizationWarningCodes",
                comparison.normalizationWarnings().stream()
                        .map(InterviewReportComparabilityPolicy.Notice::code)
                        .distinct()
                        .toList());
        item.getMetadata().put("normalizedWeaknessCount", weaknesses.size());
        return item;
    }

    private InterviewReportComparabilityPolicy.Identity identityFromComparable(
            InterviewReportComparabilityPolicy.Result comparison) {
        return new InterviewReportComparabilityPolicy.Identity(
                true,
                null,
                comparison.targetJobId(),
                comparison.rubricVersion(),
                comparabilityPolicy.dimensionFingerprint(
                        comparison.normalizedDimensions().keySet()),
                comparison.normalizationWarnings());
    }

    private List<ComparisonGroupItem> buildComparisonGroups(
            List<EvaluatedReport> evaluatedReports) {
        Map<GroupKey, GroupAccumulator> groups = new LinkedHashMap<>();
        for (EvaluatedReport evaluated : evaluatedReports) {
            InterviewReportComparabilityPolicy.Identity identity = evaluated.identity();
            if (identity == null
                    || !identity.available()
                    || identity.targetJobId() == null
                    || !StringUtils.hasText(identity.rubricVersion())
                    || !StringUtils.hasText(identity.dimensionFingerprint())) {
                continue;
            }
            GroupKey key = new GroupKey(
                    identity.targetJobId(),
                    identity.rubricVersion(),
                    identity.dimensionFingerprint());
            GroupAccumulator group = groups.computeIfAbsent(
                    key,
                    ignored -> new GroupAccumulator(key));
            if (Boolean.TRUE.equals(evaluated.item().getIncluded())) {
                group.trustedReports.add(evaluated);
            } else {
                group.excludedReports.add(evaluated);
                group.excludedReasonCounts.merge(
                        fallbackText(evaluated.item().getExcludeReason(), "UNAVAILABLE"),
                        1,
                        Integer::sum);
            }
        }
        return groups.values().stream()
                .sorted(Comparator.comparing(group -> comparisonKey(group.key)))
                .map(this::toComparisonGroup)
                .toList();
    }

    private ComparisonGroupItem toComparisonGroup(GroupAccumulator group) {
        List<EvaluatedReport> trusted = group.trustedReports.stream()
                .sorted(Comparator
                        .comparing(
                                (EvaluatedReport item) -> reportBusinessTime(item.report()),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(item -> item.report().getId(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<BigDecimal> scores = trusted.stream()
                .map(item -> item.item().getTotalScore())
                .filter(Objects::nonNull)
                .toList();

        ComparisonGroupItem item = new ComparisonGroupItem();
        item.setComparisonKey(comparisonKey(group.key));
        item.setTargetJobId(group.key.targetJobId());
        item.setRubricVersion(safeRubricVersion(group.key.rubricVersion()));
        item.setDimensionFingerprint(group.key.dimensionFingerprint());
        item.setTrustedReportCount(trusted.size());
        item.setExcludedReportCount(group.excludedReports.size());
        item.setSourceReportIds(trusted.stream()
                .map(value -> value.report().getId())
                .filter(Objects::nonNull)
                .toList());
        if (!scores.isEmpty()) {
            item.setFirstScore(scores.get(0));
            item.setLastScore(scores.get(scores.size() - 1));
            item.setAverageScore(scores.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP));
        }
        item.setDirection(direction(item.getFirstScore(), item.getLastScore(), trusted.size()));
        item.getMetadata().put("trendEligible", trusted.size() >= MIN_TREND_REPORTS);
        item.getMetadata().put("minimumTrustedReports", MIN_TREND_REPORTS);
        item.getMetadata().put(
                "excludedReasonCounts",
                orderedCountMap(group.excludedReasonCounts));
        item.getMetadata().put(
                "normalizedWeaknessCounts",
                weaknessCounts(trusted));
        return item;
    }

    private String comparisonKey(GroupKey key) {
        return "TARGET_JOB:" + key.targetJobId()
                + "|RUBRIC:" + fallbackText(safeRubricVersion(key.rubricVersion()), "UNKNOWN")
                + "|DIMENSIONS:" + key.dimensionFingerprint();
    }

    private Map<String, Integer> weaknessCounts(List<EvaluatedReport> reports) {
        Map<String, Integer> counts = new HashMap<>();
        for (EvaluatedReport report : reports) {
            new LinkedHashSet<>(report.item().getNormalizedWeaknesses())
                    .forEach(weakness -> counts.merge(weakness, 1, Integer::sum));
        }
        return orderedCountMap(counts);
    }

    private Map<String, Integer> orderedCountMap(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private String direction(BigDecimal first, BigDecimal last, int trustedCount) {
        if (trustedCount < MIN_TREND_REPORTS || first == null || last == null) {
            return "INSUFFICIENT_SAMPLE";
        }
        int comparison = last.compareTo(first);
        if (comparison > 0) {
            return "UP";
        }
        if (comparison < 0) {
            return "DOWN";
        }
        return "STABLE";
    }

    private String trustStatus(
            InterviewReport report,
            InterviewReportComparabilityPolicy.Result comparison,
            InterviewReportScoringContract.Validation scoring,
            boolean formalActionTrusted,
            boolean sampleInsufficient,
            boolean fallback) {
        if (comparison.comparable()) {
            boolean nativeContract = scoring.valid()
                    && formalActionTrusted
                    && "REPORT_RUBRIC_SCORES".equals(comparison.normalizationSource())
                    && Objects.equals(report.getRubricVersion(), comparison.rubricVersion());
            return nativeContract ? "VERIFIED" : "NORMALIZED";
        }
        if (sampleInsufficient) {
            return "SAMPLE_INSUFFICIENT";
        }
        if (fallback) {
            return "FALLBACK";
        }
        if (InterviewReportTrustPolicy.isFallbackOrUntrusted(report)) {
            return "UNTRUSTED";
        }
        return "PARTIAL";
    }

    private String exclusionReason(
            InterviewReportComparabilityPolicy.Result comparison,
            InterviewReportScoringContract.Validation scoring,
            boolean sampleInsufficient,
            boolean fallback) {
        if (sampleInsufficient) {
            return "SAMPLE_INSUFFICIENT";
        }
        if (fallback) {
            return "REPORT_FALLBACK";
        }
        if (StringUtils.hasText(comparison.reasonCode())) {
            return comparison.reasonCode();
        }
        if (!scoring.valid() && StringUtils.hasText(scoring.reasonCode())) {
            return scoring.reasonCode();
        }
        return "REPORT_UNTRUSTED";
    }

    private boolean ownedVisibleSession(InterviewSession session, RequestWindow window) {
        return session != null
                && session.getId() != null
                && Objects.equals(session.getUserId(), window.userId())
                && !isDeleted(session.getDeleted())
                && matchesTarget(session.getTargetJobId(), window.targetJobId())
                && visibleAtCutoff(
                        session.getCreatedAt(),
                        session.getUpdatedAt(),
                        firstNonNull(session.getEndTime(), session.getStartTime()),
                        window.sourceCutoffAt());
    }

    private boolean ownedVisibleReport(InterviewReport report, RequestWindow window) {
        return report != null
                && report.getId() != null
                && report.getSessionId() != null
                && Objects.equals(report.getUserId(), window.userId())
                && !isDeleted(report.getDeleted())
                && visibleAtCutoff(
                        report.getCreatedAt(),
                        report.getUpdatedAt(),
                        reportBusinessTime(report),
                        window.sourceCutoffAt());
    }

    private boolean reportActivityInWindow(
            InterviewReport report,
            InterviewSession session,
            RequestWindow window) {
        return inWindowAtCutoff(reportBusinessTime(report), window)
                || inWindowAtCutoff(session.getEndTime(), window);
    }

    private boolean inWindowAtCutoff(LocalDateTime value, RequestWindow window) {
        return value != null
                && !value.isBefore(window.rangeStartUtc())
                && value.isBefore(window.rangeEndUtc())
                && !value.isAfter(window.sourceCutoffAt());
    }

    private boolean visibleAtCutoff(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime fallbackTime,
            LocalDateTime sourceCutoffAt) {
        if (createdAt != null && createdAt.isAfter(sourceCutoffAt)) {
            return false;
        }
        if (updatedAt != null && updatedAt.isAfter(sourceCutoffAt)) {
            return false;
        }
        LocalDateTime visibleTime = firstNonNull(updatedAt, createdAt, fallbackTime);
        return visibleTime != null && !visibleTime.isAfter(sourceCutoffAt);
    }

    private boolean matchesTarget(Long recordTargetJobId, Long targetJobId) {
        return targetJobId == null || Objects.equals(recordTargetJobId, targetJobId);
    }

    private boolean isDeleted(Integer deleted) {
        return Objects.equals(deleted, CommonConstants.YES);
    }

    private List<String> normalizeWeaknesses(InterviewReport report) {
        LinkedHashSet<String> rawValues = new LinkedHashSet<>();
        appendWeaknessValues(rawValues, report.getWeakPoints());
        appendWeaknessValues(rawValues, report.getWeaknesses());
        appendWeaknessValues(rawValues, report.getMainProblems());

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            String weakness = normalizeWeakness(rawValue);
            if (StringUtils.hasText(weakness)) {
                normalized.add(weakness);
            }
            if (normalized.size() >= MAX_WEAKNESSES_PER_REPORT) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    private void appendWeaknessValues(Collection<String> values, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(rawValue);
            appendWeaknessJson(values, root, 0);
            return;
        } catch (Exception ignored) {
            // Historical reports may store a short delimiter-separated value.
        }
        for (String part : rawValue.split("[\\r\\n,，;；、|]+")) {
            if (StringUtils.hasText(part)) {
                values.add(part.trim());
            }
        }
    }

    private void appendWeaknessJson(Collection<String> values, JsonNode node, int depth) {
        if (node == null || node.isNull() || depth > 4) {
            return;
        }
        if (node.isTextual()) {
            if (StringUtils.hasText(node.textValue())) {
                values.add(node.textValue().trim());
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> appendWeaknessJson(values, item, depth + 1));
            return;
        }
        if (node.isObject()) {
            for (String field : WEAKNESS_JSON_FIELDS) {
                appendWeaknessJson(values, node.get(field), depth + 1);
            }
        }
    }

    private String normalizeWeakness(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String compact = rawValue.trim().replaceAll("\\s+", " ");
        String lowered = compact.toLowerCase(Locale.ROOT);
        if (containsAny(lowered, "system design", "系统设计", "架构设计", "架构能力")) {
            return "SYSTEM_DESIGN";
        }
        if (containsAny(lowered, "technical depth", "技术深度", "源码细节")) {
            return "TECHNICAL_DEPTH";
        }
        if (containsAny(lowered, "expression structure", "项目表达", "表达结构", "沟通表达")) {
            return "EXPRESSION_STRUCTURE";
        }
        if (containsAny(lowered, "business understanding", "业务理解")) {
            return "BUSINESS_UNDERSTANDING";
        }
        if (containsAny(lowered, "risk awareness", "风险意识", "容错")) {
            return "RISK_AWARENESS";
        }
        if (containsAny(lowered, "implementability", "落地能力", "可实施")) {
            return "IMPLEMENTABILITY";
        }
        if (containsAny(lowered, "redis", "cache", "缓存")) {
            return "CACHE";
        }
        if (containsAny(lowered, "mysql", "sql", "database", "数据库", "索引", "事务")) {
            return "DATABASE";
        }
        if (containsAny(lowered, "concurrency", "并发", "线程", "线程池", "锁")) {
            return "CONCURRENCY";
        }
        if (containsAny(lowered, "jvm", "java 基础", "java基础")) {
            return "JAVA_FOUNDATION";
        }
        if (containsAny(lowered, "spring")) {
            return "SPRING";
        }
        if (containsAny(lowered, "microservice", "微服务")) {
            return "MICROSERVICES";
        }
        if (containsAny(lowered, "rocketmq", "kafka", "消息队列")) {
            return "MESSAGING";
        }
        if (containsAny(lowered, "algorithm", "算法")) {
            return "ALGORITHM";
        }
        if (containsAny(lowered, "tcp", "http", "network", "网络")) {
            return "NETWORK";
        }

        String code = compact.toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        if (SAFE_CODE_PATTERN.matcher(code).matches() && !looksSensitive(code)) {
            return code;
        }
        return "WEAKNESS_HASH_" + sha256(compact).substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String safeRubricVersion(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (SAFE_CODE_PATTERN.matcher(normalized).matches() && !looksSensitive(normalized)) {
            return normalized;
        }
        return "RUBRIC_HASH_" + sha256(value.trim()).substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private boolean looksSensitive(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        return EMAIL_PATTERN.matcher(value).matches()
                || LONG_DIGIT_PATTERN.matcher(value).matches()
                || normalized.contains("TOKEN")
                || normalized.contains("SECRET")
                || normalized.contains("PASSWORD")
                || normalized.contains("API_KEY")
                || normalized.contains("API-KEY");
    }

    private String normalizeCode(String value, Set<String> allowedValues) {
        if (!StringUtils.hasText(value)) {
            return "UNKNOWN";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return allowedValues.contains(normalized) ? normalized : "UNKNOWN";
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> enumType) {
        Set<String> names = new LinkedHashSet<>();
        for (Enum<?> value : enumType.getEnumConstants()) {
            names.add(value.name());
        }
        return Set.copyOf(names);
    }

    private String buildSessionSummary(SessionItem item) {
        return "面试会话#" + item.getSessionId()
                + "；模式=" + item.getMode()
                + "；状态=" + item.getStatus()
                + "；完成=" + item.getCompletedAt();
    }

    private String buildReportSummary(ReportItem item) {
        return "面试报告#" + item.getReportId()
                + "；状态=" + item.getReportStatus()
                + "；信任=" + item.getTrustStatus()
                + "；总分=" + item.getTotalScore()
                + "；量表=" + item.getRubricVersion()
                + "；规范弱项=" + item.getNormalizedWeaknesses().size();
    }

    private void fillSourceCounts(
            WeeklyInterviewEvidenceVO result,
            List<SessionItem> sessions,
            List<ReportItem> reports,
            List<ComparisonGroupItem> groups,
            CollectionState state) {
        int includedReports = (int) reports.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIncluded()))
                .count();
        int excludedReports = reports.size() - includedReports + state.droppedCount("reports");
        putSourceCounts(
                result.getSourceCounts(),
                "sessions",
                sessions.size(),
                state.droppedCount("sessions"),
                state.truncatedCount("sessions"));
        putSourceCounts(
                result.getSourceCounts(),
                "reports",
                includedReports,
                excludedReports,
                state.truncatedCount("reports"));
        result.getSourceCounts().put("comparisonGroups.total", groups.size());
        result.getSourceCounts().put(
                "comparisonGroups.trendEligible",
                (int) groups.stream()
                        .filter(group -> group.getTrustedReportCount() >= MIN_TREND_REPORTS)
                        .count());
    }

    private void putSourceCounts(
            Map<String, Integer> counts,
            String source,
            int included,
            int excluded,
            int truncated) {
        counts.put(source + ".included", included);
        counts.put(source + ".excluded", excluded);
        counts.put(source + ".truncated", truncated);
        counts.put(source + ".total", included + excluded);
    }

    private List<InterviewReport> sortReports(List<InterviewReport> reports) {
        return safeList(reports).stream()
                .sorted(Comparator
                        .comparing(
                                this::reportBusinessTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                InterviewReport::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<InterviewSession> sortSessions(List<InterviewSession> sessions) {
        return safeList(sessions).stream()
                .sorted(Comparator
                        .comparing(
                                InterviewSession::getEndTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                InterviewSession::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private Comparator<InterviewSession> sessionComparator(
            Map<Long, LocalDateTime> firstReportActivityBySession) {
        return Comparator
                .comparing(
                        (InterviewSession session) -> firstNonNull(
                                session.getEndTime(),
                                firstReportActivityBySession.get(session.getId()),
                                session.getStartTime(),
                                session.getCreatedAt()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                        InterviewSession::getId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private <T> List<T> trim(
            List<T> values,
            int maxSize,
            String source,
            String warning,
            CollectionState state) {
        List<T> safe = safeList(values);
        if (safe.size() <= maxSize) {
            return new ArrayList<>(safe);
        }
        state.truncate(source, safe.size() - maxSize, warning);
        return new ArrayList<>(safe.subList(0, maxSize));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private LocalDateTime reportBusinessTime(InterviewReport report) {
        return report == null ? null : firstNonNull(report.getGeneratedAt(), report.getCreatedAt());
    }

    private LocalDateTime earlierNonNull(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private boolean validScore(Integer score) {
        return score != null && score >= 1 && score <= 100;
    }

    private String sourceHash(String sourceType, Object... values) {
        StringBuilder canonical = new StringBuilder(sourceType);
        for (Object value : values) {
            String text = value == null ? "<null>" : String.valueOf(value);
            canonical.append('|').append(text.length()).append(':').append(text);
        }
        return "sha256:" + sha256(canonical.toString());
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "面试周证据哈希生成失败");
        }
    }

    private void putIfText(Map<String, Object> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value);
        }
    }

    private String fallbackText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
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

    private record RequestWindow(
            Long userId,
            LocalDateTime rangeStartUtc,
            LocalDateTime rangeEndUtc,
            LocalDateTime sourceCutoffAt,
            Long targetJobId) {
    }

    private record SessionCollection(
            Map<Long, InterviewSession> sessionsById,
            Set<Long> completedSessionIds) {
    }

    private record ReportCollection(
            List<ReportItem> reportItems,
            List<EvaluatedReport> evaluatedReports,
            Set<Long> activeSessionIds,
            Map<Long, LocalDateTime> firstReportActivityBySession) {
    }

    private record EvaluatedReport(
            InterviewReport report,
            ReportItem item,
            InterviewReportComparabilityPolicy.Result comparison,
            InterviewReportComparabilityPolicy.Identity identity) {
    }

    private record GroupKey(
            Long targetJobId,
            String rubricVersion,
            String dimensionFingerprint) {
    }

    private static final class GroupAccumulator {

        private final GroupKey key;
        private final List<EvaluatedReport> trustedReports = new ArrayList<>();
        private final List<EvaluatedReport> excludedReports = new ArrayList<>();
        private final Map<String, Integer> excludedReasonCounts = new HashMap<>();

        private GroupAccumulator(GroupKey key) {
            this.key = key;
        }
    }

    private static final class CollectionState {

        private final Map<String, Integer> droppedCounts = new HashMap<>();
        private final Map<String, Integer> truncatedCounts = new HashMap<>();
        private final LinkedHashSet<String> warnings = new LinkedHashSet<>();
        private boolean truncated;
        private boolean partial;

        private void drop(String source, String warning) {
            droppedCounts.merge(source, 1, Integer::sum);
            partial(warning);
        }

        private void partial(String warning) {
            warnings.add(warning);
            partial = true;
        }

        private void warn(String warning) {
            warnings.add(warning);
        }

        private void truncate(String source, int count, String warning) {
            truncatedCounts.merge(source, count, Integer::sum);
            warnings.add(warning);
            truncated = true;
        }

        private int droppedCount(String source) {
            return droppedCounts.getOrDefault(source, 0);
        }

        private int truncatedCount(String source) {
            return truncatedCounts.getOrDefault(source, 0);
        }
    }
}
