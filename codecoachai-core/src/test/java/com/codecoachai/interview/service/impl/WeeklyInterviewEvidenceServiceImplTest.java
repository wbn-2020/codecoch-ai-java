package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.WeeklyInterviewEvidenceVO;
import com.codecoachai.interview.domain.vo.WeeklyInterviewEvidenceVO.ComparisonGroupItem;
import com.codecoachai.interview.domain.vo.WeeklyInterviewEvidenceVO.ReportItem;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.support.InterviewReportComparabilityPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyInterviewEvidenceServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long TARGET_JOB_ID = 20L;
    private static final LocalDateTime RANGE_START =
            LocalDateTime.of(2026, 7, 12, 16, 0);
    private static final LocalDateTime RANGE_END =
            LocalDateTime.of(2026, 7, 19, 16, 0);
    private static final LocalDateTime CUTOFF =
            LocalDateTime.of(2026, 7, 18, 12, 0);
    private static final String RUBRIC_AB = """
            [
              {"dimension":"TECHNICAL_DEPTH","score":4},
              {"dimension":"EXPRESSION_STRUCTURE","score":3}
            ]
            """;

    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewReportMapper reportMapper;

    private ObjectMapper objectMapper;
    private WeeklyInterviewEvidenceServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new WeeklyInterviewEvidenceServiceImpl(
                sessionMapper,
                reportMapper,
                objectMapper,
                new InterviewReportComparabilityPolicy(objectMapper));
    }

    @Test
    void returnsTrustedComparableSamplesAndTrendGroup() {
        List<InterviewSession> sessions = List.of(
                session(101L, USER_ID, TARGET_JOB_ID, RANGE_START.plusDays(1)),
                session(102L, USER_ID, TARGET_JOB_ID, RANGE_START.plusDays(2)),
                session(103L, USER_ID, TARGET_JOB_ID, RANGE_START.plusDays(3)));
        List<InterviewReport> reports = List.of(
                trustedReport(201L, 101L, USER_ID, 70, "RUBRIC_V1", RUBRIC_AB,
                        RANGE_START.plusDays(1).plusMinutes(10)),
                trustedReport(202L, 102L, USER_ID, 80, "RUBRIC_V1", RUBRIC_AB,
                        RANGE_START.plusDays(2).plusMinutes(10)),
                trustedReport(203L, 103L, USER_ID, 90, "RUBRIC_V1", RUBRIC_AB,
                        RANGE_START.plusDays(3).plusMinutes(10)));
        reports.forEach(report -> report.setWeakPoints("[\"系统设计\",\"数据库\"]"));
        stubSources(null, sessions, sessions, reports, CUTOFF);

        WeeklyInterviewEvidenceVO result = collect(null, CUTOFF);

        assertEquals(USER_ID, result.getUserId());
        assertEquals(3, result.getSessions().size());
        assertEquals(3, result.getReports().size());
        assertTrue(result.getReports().stream().allMatch(ReportItem::getIncluded));
        assertTrue(result.getReports().stream()
                .allMatch(report -> "VERIFIED".equals(report.getTrustStatus())));
        assertEquals(
                List.of("SYSTEM_DESIGN", "DATABASE"),
                result.getReports().get(0).getNormalizedWeaknesses());

        assertEquals(1, result.getComparisonGroups().size());
        ComparisonGroupItem group = result.getComparisonGroups().get(0);
        assertEquals(3, group.getTrustedReportCount());
        assertEquals(0, group.getExcludedReportCount());
        assertEquals(new BigDecimal("70"), group.getFirstScore());
        assertEquals(new BigDecimal("90"), group.getLastScore());
        assertEquals(new BigDecimal("80.00"), group.getAverageScore());
        assertEquals("UP", group.getDirection());
        assertEquals(List.of(201L, 202L, 203L), group.getSourceReportIds());
        assertEquals(
                Map.of("DATABASE", 3, "SYSTEM_DESIGN", 3),
                group.getMetadata().get("normalizedWeaknessCounts"));
        assertEquals("COMPLETE", result.getConsistencyLevel());
        assertFalse(result.getTruncated());
        assertEquals(3, result.getSourceCounts().get("reports.included"));
        assertEquals(0, result.getSourceCounts().get("reports.excluded"));
    }

    @Test
    void excludesFallbackLowTrustAndSampleInsufficientReports() {
        List<InterviewSession> sessions = List.of(
                session(101L, USER_ID, TARGET_JOB_ID, RANGE_START.plusDays(1)),
                session(102L, USER_ID, TARGET_JOB_ID, RANGE_START.plusDays(2)),
                session(103L, USER_ID, TARGET_JOB_ID, RANGE_START.plusDays(3)));

        InterviewReport fallback = trustedReport(
                201L,
                101L,
                USER_ID,
                70,
                "RUBRIC_V1",
                """
                [
                  {"dimension":"TECHNICAL_DEPTH","score":4,"fallback":true},
                  {"dimension":"EXPRESSION_STRUCTURE","score":3}
                ]
                """,
                RANGE_START.plusDays(1).plusMinutes(10));
        InterviewReport lowTrust = trustedReport(
                202L,
                102L,
                USER_ID,
                75,
                "RUBRIC_V1",
                RUBRIC_AB,
                RANGE_START.plusDays(2).plusMinutes(10));
        lowTrust.setAdviceEvidence("[{\"evidenceSources\":[]}]");
        InterviewReport sampleInsufficient = trustedReport(
                203L,
                103L,
                USER_ID,
                80,
                "RUBRIC_V1",
                """
                [
                  {"dimension":"TECHNICAL_DEPTH","score":4,"sampleInsufficient":true},
                  {"dimension":"EXPRESSION_STRUCTURE","score":3}
                ]
                """,
                RANGE_START.plusDays(3).plusMinutes(10));
        stubSources(
                null,
                sessions,
                sessions,
                List.of(fallback, lowTrust, sampleInsufficient),
                CUTOFF);

        WeeklyInterviewEvidenceVO result = collect(null, CUTOFF);

        assertEquals(3, result.getReports().size());
        ReportItem fallbackItem = report(result, 201L);
        assertFalse(fallbackItem.getIncluded());
        assertTrue(fallbackItem.getFallback());
        assertEquals("FALLBACK", fallbackItem.getTrustStatus());
        assertEquals("REPORT_FALLBACK", fallbackItem.getExcludeReason());

        ReportItem lowTrustItem = report(result, 202L);
        assertFalse(lowTrustItem.getIncluded());
        assertFalse(lowTrustItem.getFallback());
        assertFalse(lowTrustItem.getSampleInsufficient());
        assertEquals("REPORT_UNTRUSTED", lowTrustItem.getExcludeReason());

        ReportItem insufficientItem = report(result, 203L);
        assertFalse(insufficientItem.getIncluded());
        assertTrue(insufficientItem.getSampleInsufficient());
        assertEquals("SAMPLE_INSUFFICIENT", insufficientItem.getTrustStatus());
        assertEquals("SAMPLE_INSUFFICIENT", insufficientItem.getExcludeReason());

        assertEquals(1, result.getComparisonGroups().size());
        assertEquals(0, result.getComparisonGroups().get(0).getTrustedReportCount());
        assertEquals(3, result.getComparisonGroups().get(0).getExcludedReportCount());
        assertEquals(
                "INSUFFICIENT_SAMPLE",
                result.getComparisonGroups().get(0).getDirection());
        assertEquals(0, result.getSourceCounts().get("reports.included"));
        assertEquals(3, result.getSourceCounts().get("reports.excluded"));
        assertEquals("PARTIAL", result.getConsistencyLevel());
        assertTrue(result.getWarnings().stream()
                .allMatch(value -> value.matches("[A-Z0-9_]+")));
        assertTrue(result.getReports().stream()
                .map(ReportItem::getExcludeReason)
                .filter(Objects::nonNull)
                .allMatch(value -> value.matches("[A-Z0-9_]+")));
    }

    @Test
    void isolatesDifferentRubricVersionsAndDimensionFingerprints() {
        List<InterviewSession> sessions = IntStream.rangeClosed(1, 6)
                .mapToObj(index -> session(
                        100L + index,
                        USER_ID,
                        TARGET_JOB_ID,
                        RANGE_START.plusHours(index * 12L)))
                .toList();
        String rubricAc = """
                [
                  {"dimension":"TECHNICAL_DEPTH","score":4},
                  {"dimension":"BUSINESS_UNDERSTANDING","score":3}
                ]
                """;
        List<InterviewReport> reports = List.of(
                trustedReport(201L, 101L, USER_ID, 70, "RUBRIC_V1", RUBRIC_AB,
                        RANGE_START.plusDays(1).plusMinutes(1)),
                trustedReport(202L, 102L, USER_ID, 72, "RUBRIC_V1", RUBRIC_AB,
                        RANGE_START.plusDays(2).plusMinutes(1)),
                trustedReport(203L, 103L, USER_ID, 74, "RUBRIC_V2", RUBRIC_AB,
                        RANGE_START.plusDays(3).plusMinutes(1)),
                trustedReport(204L, 104L, USER_ID, 76, "RUBRIC_V2", RUBRIC_AB,
                        RANGE_START.plusDays(4).plusMinutes(1)),
                trustedReport(205L, 105L, USER_ID, 78, "RUBRIC_V1", rubricAc,
                        RANGE_START.plusDays(5).plusMinutes(1)),
                trustedReport(206L, 106L, USER_ID, 80, "RUBRIC_V1", rubricAc,
                        RANGE_START.plusDays(5).plusHours(2)));
        stubSources(null, sessions, sessions, reports, CUTOFF);

        WeeklyInterviewEvidenceVO result = collect(null, CUTOFF);

        assertEquals(3, result.getComparisonGroups().size());
        assertEquals(
                List.of(2, 2, 2),
                result.getComparisonGroups().stream()
                        .map(ComparisonGroupItem::getTrustedReportCount)
                        .sorted()
                        .toList());
        assertEquals(
                3,
                result.getComparisonGroups().stream()
                        .map(ComparisonGroupItem::getComparisonKey)
                        .distinct()
                        .count());
        ComparisonGroupItem rubricV1Ab = result.getComparisonGroups().stream()
                .filter(group -> "RUBRIC_V1".equals(group.getRubricVersion()))
                .filter(group -> group.getSourceReportIds().contains(201L))
                .findFirst()
                .orElseThrow();
        ComparisonGroupItem rubricV2Ab = result.getComparisonGroups().stream()
                .filter(group -> "RUBRIC_V2".equals(group.getRubricVersion()))
                .findFirst()
                .orElseThrow();
        ComparisonGroupItem rubricV1Ac = result.getComparisonGroups().stream()
                .filter(group -> "RUBRIC_V1".equals(group.getRubricVersion()))
                .filter(group -> group.getSourceReportIds().contains(205L))
                .findFirst()
                .orElseThrow();
        assertEquals(
                rubricV1Ab.getDimensionFingerprint(),
                rubricV2Ab.getDimensionFingerprint());
        assertNotEquals(
                rubricV1Ab.getDimensionFingerprint(),
                rubricV1Ac.getDimensionFingerprint());
        assertTrue(result.getComparisonGroups().stream()
                .allMatch(group -> "INSUFFICIENT_SAMPLE".equals(group.getDirection())));
    }

    @Test
    void appliesStartInclusiveEndExclusiveWindow() {
        LocalDateTime cutoffAfterRange = RANGE_END.plusHours(1);
        InterviewSession atStart = session(101L, USER_ID, TARGET_JOB_ID, RANGE_START);
        InterviewSession beforeEnd =
                session(102L, USER_ID, TARGET_JOB_ID, RANGE_END.minusNanos(1));
        InterviewSession atEnd = session(103L, USER_ID, TARGET_JOB_ID, RANGE_END);
        InterviewSession beforeStart =
                session(104L, USER_ID, TARGET_JOB_ID, RANGE_START.minusNanos(1));

        InterviewSession reportAtStartSession =
                session(111L, USER_ID, TARGET_JOB_ID, RANGE_START.minusDays(1));
        InterviewSession reportBeforeEndSession =
                session(112L, USER_ID, TARGET_JOB_ID, RANGE_START.minusDays(1));
        InterviewSession reportAtEndSession =
                session(113L, USER_ID, TARGET_JOB_ID, RANGE_START.minusDays(1));
        InterviewSession reportBeforeStartSession =
                session(114L, USER_ID, TARGET_JOB_ID, RANGE_START.minusDays(1));
        List<InterviewReport> reports = List.of(
                trustedReport(211L, 111L, USER_ID, 70, "RUBRIC_V1", RUBRIC_AB, RANGE_START),
                trustedReport(
                        212L,
                        112L,
                        USER_ID,
                        71,
                        "RUBRIC_V1",
                        RUBRIC_AB,
                        RANGE_END.minusNanos(1)),
                trustedReport(213L, 113L, USER_ID, 72, "RUBRIC_V1", RUBRIC_AB, RANGE_END),
                trustedReport(
                        214L,
                        114L,
                        USER_ID,
                        73,
                        "RUBRIC_V1",
                        RUBRIC_AB,
                        RANGE_START.minusNanos(1)));
        stubSources(
                null,
                List.of(atStart, beforeEnd, atEnd, beforeStart),
                List.of(
                        reportAtStartSession,
                        reportBeforeEndSession,
                        reportAtEndSession,
                        reportBeforeStartSession),
                reports,
                cutoffAfterRange);

        WeeklyInterviewEvidenceVO result = collect(null, cutoffAfterRange);

        assertEquals(
                List.of(111L, 112L, 101L, 102L),
                result.getSessions().stream()
                        .map(WeeklyInterviewEvidenceVO.SessionItem::getSessionId)
                        .toList());
        assertEquals(
                List.of(211L, 212L),
                result.getReports().stream().map(ReportItem::getReportId).toList());
        assertEquals(2, result.getSourceCounts().get("sessions.excluded"));
        assertEquals(2, result.getSourceCounts().get("reports.excluded"));
        assertEquals("PARTIAL", result.getConsistencyLevel());
    }

    @Test
    void includesCutoffBoundaryAndExcludesLaterRecords() {
        LocalDateTime cutoff = RANGE_START.plusDays(2);
        InterviewSession atCutoff = session(101L, USER_ID, TARGET_JOB_ID, cutoff);
        InterviewSession afterCutoff =
                session(102L, USER_ID, TARGET_JOB_ID, cutoff.plusNanos(1));
        InterviewSession reportSession =
                session(103L, USER_ID, TARGET_JOB_ID, RANGE_START.plusHours(1));

        InterviewReport reportAtCutoff = trustedReport(
                201L,
                103L,
                USER_ID,
                70,
                "RUBRIC_V1",
                RUBRIC_AB,
                cutoff);
        InterviewReport reportAfterCutoff = trustedReport(
                202L,
                103L,
                USER_ID,
                72,
                "RUBRIC_V1",
                RUBRIC_AB,
                cutoff.plusNanos(1));
        stubSources(
                null,
                List.of(atCutoff, afterCutoff, reportSession),
                List.of(reportSession),
                List.of(reportAtCutoff, reportAfterCutoff),
                cutoff);

        WeeklyInterviewEvidenceVO result = collect(null, cutoff);

        assertTrue(result.getSessions().stream()
                .map(WeeklyInterviewEvidenceVO.SessionItem::getSessionId)
                .toList()
                .contains(101L));
        assertFalse(result.getSessions().stream()
                .map(WeeklyInterviewEvidenceVO.SessionItem::getSessionId)
                .toList()
                .contains(102L));
        assertEquals(List.of(201L),
                result.getReports().stream().map(ReportItem::getReportId).toList());
        assertEquals(1, result.getSourceCounts().get("reports.excluded"));
    }

    @Test
    void returnsCompleteEmptyEvidenceForEmptySample() {
        stubSources(null, List.of(), List.of(), List.of(), CUTOFF);

        WeeklyInterviewEvidenceVO result = collect(null, CUTOFF);

        assertTrue(result.getSessions().isEmpty());
        assertTrue(result.getReports().isEmpty());
        assertTrue(result.getComparisonGroups().isEmpty());
        assertEquals("COMPLETE", result.getConsistencyLevel());
        assertFalse(result.getTruncated());
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(0, result.getSourceCounts().get("sessions.total"));
        assertEquals(0, result.getSourceCounts().get("reports.total"));
        assertEquals(0, result.getSourceCounts().get("comparisonGroups.total"));
    }

    @Test
    void failsClosedForForeignAndDeletedRows() {
        InterviewSession owned =
                session(101L, USER_ID, TARGET_JOB_ID, RANGE_START.plusDays(1));
        InterviewSession foreign =
                session(102L, 99L, TARGET_JOB_ID, RANGE_START.plusDays(2));
        InterviewSession deleted =
                session(103L, USER_ID, TARGET_JOB_ID, RANGE_START.plusDays(3));
        deleted.setDeleted(1);

        InterviewReport ownedReport = trustedReport(
                201L, 101L, USER_ID, 70, "RUBRIC_V1", RUBRIC_AB, RANGE_START.plusDays(1));
        InterviewReport foreignReport = trustedReport(
                202L, 102L, 99L, 72, "RUBRIC_V1", RUBRIC_AB, RANGE_START.plusDays(2));
        InterviewReport deletedReport = trustedReport(
                203L, 103L, USER_ID, 74, "RUBRIC_V1", RUBRIC_AB, RANGE_START.plusDays(3));
        deletedReport.setDeleted(1);
        stubSources(
                null,
                List.of(owned, foreign, deleted),
                List.of(owned, foreign, deleted),
                List.of(ownedReport, foreignReport, deletedReport),
                CUTOFF);

        WeeklyInterviewEvidenceVO result = collect(null, CUTOFF);

        assertEquals(
                List.of(101L),
                result.getSessions().stream()
                        .map(WeeklyInterviewEvidenceVO.SessionItem::getSessionId)
                        .toList());
        assertEquals(
                List.of(201L),
                result.getReports().stream().map(ReportItem::getReportId).toList());
        assertTrue(result.getSourceCounts().get("sessions.excluded") >= 2);
        assertEquals(2, result.getSourceCounts().get("reports.excluded"));
        assertEquals("PARTIAL", result.getConsistencyLevel());
    }

    @Test
    void appliesTargetJobScopeInQueriesAndFailClosedRevalidation() {
        InterviewSession matching =
                session(101L, USER_ID, TARGET_JOB_ID, RANGE_START.plusDays(1));
        InterviewSession other =
                session(102L, USER_ID, 21L, RANGE_START.plusDays(2));
        InterviewReport matchingReport = trustedReport(
                201L, 101L, USER_ID, 70, "RUBRIC_V1", RUBRIC_AB, RANGE_START.plusDays(1));
        InterviewReport otherReport = trustedReport(
                202L, 102L, USER_ID, 72, "RUBRIC_V1", RUBRIC_AB, RANGE_START.plusDays(2));
        stubSources(
                TARGET_JOB_ID,
                List.of(matching, other),
                List.of(matching, other),
                List.of(matchingReport, otherReport),
                CUTOFF);

        WeeklyInterviewEvidenceVO result = collect(TARGET_JOB_ID, CUTOFF);

        assertEquals(
                List.of(101L),
                result.getSessions().stream()
                        .map(WeeklyInterviewEvidenceVO.SessionItem::getSessionId)
                        .toList());
        assertEquals(
                List.of(201L),
                result.getReports().stream().map(ReportItem::getReportId).toList());
        verify(sessionMapper).selectWeeklyEvidenceSessions(
                USER_ID,
                RANGE_START,
                RANGE_END,
                CUTOFF,
                TARGET_JOB_ID,
                WeeklyInterviewEvidenceServiceImpl.MAX_SESSIONS + 1);
        verify(reportMapper).selectWeeklyEvidenceReports(
                USER_ID,
                RANGE_START,
                RANGE_END,
                CUTOFF,
                TARGET_JOB_ID,
                WeeklyInterviewEvidenceServiceImpl.MAX_REPORTS + 1);
        verify(sessionMapper).selectWeeklyEvidenceSessionsByIds(
                eq(USER_ID),
                anyList(),
                eq(CUTOFF),
                eq(TARGET_JOB_ID));
    }

    @Test
    void truncatesReportsAndSessionsAtConfiguredLimits() {
        List<InterviewSession> sessions = new ArrayList<>();
        List<InterviewReport> reports = new ArrayList<>();
        for (int index = 0; index <= WeeklyInterviewEvidenceServiceImpl.MAX_REPORTS; index++) {
            long sessionId = 1000L + index;
            LocalDateTime activityTime = RANGE_START.plusMinutes(index);
            sessions.add(session(sessionId, USER_ID, TARGET_JOB_ID, activityTime));
            reports.add(trustedReport(
                    2000L + index,
                    sessionId,
                    USER_ID,
                    60 + index % 20,
                    "RUBRIC_V1",
                    RUBRIC_AB,
                    activityTime.plusSeconds(1)));
        }
        stubSources(null, sessions, sessions, reports, CUTOFF);

        WeeklyInterviewEvidenceVO result = collect(null, CUTOFF);

        assertEquals(WeeklyInterviewEvidenceServiceImpl.MAX_REPORTS, result.getReports().size());
        assertEquals(WeeklyInterviewEvidenceServiceImpl.MAX_SESSIONS, result.getSessions().size());
        assertTrue(result.getTruncated());
        assertEquals("BEST_EFFORT", result.getConsistencyLevel());
        assertTrue(result.getWarnings().contains("REPORTS_TRUNCATED"));
        assertTrue(result.getWarnings().contains("SESSIONS_TRUNCATED"));
        assertEquals(1, result.getSourceCounts().get("reports.truncated"));
        assertEquals(1, result.getSourceCounts().get("sessions.truncated"));
        assertEquals(
                WeeklyInterviewEvidenceServiceImpl.MAX_REPORTS,
                result.getComparisonGroups().get(0).getTrustedReportCount());
    }

    @Test
    void returnsOnlySafeSummariesAndNormalizedWeaknesses() throws Exception {
        InterviewSession session =
                session(101L, USER_ID, TARGET_JOB_ID, RANGE_START.plusDays(1));
        session.setTitle("Alice alice@example.com 13800138000");
        InterviewReport report = trustedReport(
                201L,
                101L,
                USER_ID,
                70,
                "RUBRIC_V1",
                RUBRIC_AB,
                RANGE_START.plusDays(1).plusMinutes(1));
        report.setSummary("Raw answer alice@example.com token=top-secret");
        report.setReportContent(
                "prompt=private; audio transcript; phone=13800138000; full raw answer");
        report.setWeakPoints("[\"alice@example.com\",\"数据库\"]");
        stubSources(null, List.of(session), List.of(session), List.of(report), CUTOFF);

        WeeklyInterviewEvidenceVO result = collect(null, CUTOFF);
        String json = objectMapper.writeValueAsString(result);

        assertFalse(json.contains("alice@example.com"));
        assertFalse(json.contains("13800138000"));
        assertFalse(json.contains("top-secret"));
        assertFalse(json.contains("audio transcript"));
        assertFalse(json.contains("full raw answer"));
        assertFalse(json.contains("Interview session"));
        assertFalse(json.contains("Interview report"));
        assertTrue(result.getSessions().get(0).getSafeSummary().startsWith("面试会话#"));
        assertTrue(result.getReports().get(0).getSafeSummary().startsWith("面试报告#"));
        assertTrue(result.getReports().get(0).getNormalizedWeaknesses().contains("DATABASE"));
        assertTrue(result.getReports().get(0).getNormalizedWeaknesses().stream()
                .anyMatch(value -> value.startsWith("WEAKNESS_HASH_")));
        assertTrue(result.getReports().get(0).getSourceHash()
                .matches("sha256:[0-9a-f]{64}"));
        assertTrue(result.getReports().get(0).getSafeSummary().length() <= 500);
    }

    @Test
    void rejectsInvalidRequestWithChineseMessagesBeforeQuerying() {
        assertValidationMessage(
                "userId 必须为正数",
                () -> service.getWeeklyEvidence(
                        0L,
                        RANGE_START,
                        RANGE_END,
                        CUTOFF,
                        null,
                        "Asia/Shanghai"));
        assertValidationMessage(
                "rangeStartUtc、rangeEndUtc 和 sourceCutoffAt 不能为空",
                () -> service.getWeeklyEvidence(
                        USER_ID,
                        null,
                        RANGE_END,
                        CUTOFF,
                        null,
                        "Asia/Shanghai"));
        assertValidationMessage(
                "rangeStartUtc 必须早于 rangeEndUtc",
                () -> service.getWeeklyEvidence(
                        USER_ID,
                        RANGE_START,
                        RANGE_START,
                        CUTOFF,
                        null,
                        "Asia/Shanghai"));
        assertValidationMessage(
                "sourceCutoffAt 不能早于 rangeStartUtc",
                () -> service.getWeeklyEvidence(
                        USER_ID,
                        RANGE_START,
                        RANGE_END,
                        RANGE_START.minusNanos(1),
                        null,
                        "Asia/Shanghai"));
        assertValidationMessage(
                "targetJobId 必须为正数",
                () -> service.getWeeklyEvidence(
                        USER_ID,
                        RANGE_START,
                        RANGE_END,
                        CUTOFF,
                        0L,
                        "Asia/Shanghai"));
        assertValidationMessage(
                "timezone 必须是有效的 ZoneId",
                () -> service.getWeeklyEvidence(
                        USER_ID,
                        RANGE_START,
                        RANGE_END,
                        CUTOFF,
                        null,
                        "Not/A_Zone"));

        verifyNoInteractions(sessionMapper, reportMapper);
    }

    private void assertValidationMessage(String expectedMessage, Runnable invocation) {
        BusinessException exception = assertThrows(BusinessException.class, invocation::run);
        assertEquals(expectedMessage, exception.getMessage());
    }

    private WeeklyInterviewEvidenceVO collect(Long targetJobId, LocalDateTime cutoff) {
        return service.getWeeklyEvidence(
                USER_ID,
                RANGE_START,
                RANGE_END,
                cutoff,
                targetJobId,
                "Asia/Shanghai");
    }

    private ReportItem report(WeeklyInterviewEvidenceVO result, Long reportId) {
        return result.getReports().stream()
                .filter(item -> Objects.equals(item.getReportId(), reportId))
                .findFirst()
                .orElseThrow();
    }

    private void stubSources(
            Long targetJobId,
            List<InterviewSession> completedSessions,
            List<InterviewSession> linkedSessions,
            List<InterviewReport> reports,
            LocalDateTime cutoff) {
        when(reportMapper.selectWeeklyEvidenceReports(
                USER_ID,
                RANGE_START,
                RANGE_END,
                cutoff,
                targetJobId,
                WeeklyInterviewEvidenceServiceImpl.MAX_REPORTS + 1))
                .thenReturn(reports);
        when(sessionMapper.selectWeeklyEvidenceSessions(
                USER_ID,
                RANGE_START,
                RANGE_END,
                cutoff,
                targetJobId,
                WeeklyInterviewEvidenceServiceImpl.MAX_SESSIONS + 1))
                .thenReturn(completedSessions);
        if (!reports.isEmpty()) {
            when(sessionMapper.selectWeeklyEvidenceSessionsByIds(
                    eq(USER_ID),
                    anyList(),
                    eq(cutoff),
                    eq(targetJobId)))
                    .thenAnswer(invocation -> {
                        List<Long> requestedIds = invocation.getArgument(1);
                        return linkedSessions.stream()
                                .filter(session -> requestedIds.contains(session.getId()))
                                .toList();
                    });
        }
    }

    private InterviewSession session(
            Long id,
            Long userId,
            Long targetJobId,
            LocalDateTime completedAt) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setUserId(userId);
        session.setTargetJobId(targetJobId);
        session.setApplicationId(5000L + id);
        session.setMode("COMPREHENSIVE");
        session.setStatus("COMPLETED");
        session.setReportStatus("GENERATED");
        session.setStartTime(completedAt.minusHours(1));
        session.setEndTime(completedAt);
        session.setTotalScore(80);
        session.setCreatedAt(RANGE_START.minusDays(1));
        session.setUpdatedAt(completedAt);
        session.setDeleted(0);
        return session;
    }

    private InterviewReport trustedReport(
            Long id,
            Long sessionId,
            Long userId,
            int score,
            String rubricVersion,
            String rubricScores,
            LocalDateTime generatedAt) {
        InterviewReport report = new InterviewReport();
        report.setId(id);
        report.setSessionId(sessionId);
        report.setUserId(userId);
        report.setStatus("GENERATED");
        report.setTotalScore(score);
        report.setRubricVersion(rubricVersion);
        report.setRubricScores(rubricScores);
        report.setSummary("trusted summary");
        report.setReportContent("{\"safe\":\"structured report\"}");
        report.setGeneratedAt(generatedAt);
        report.setCreatedAt(RANGE_START.minusHours(1));
        report.setUpdatedAt(generatedAt);
        report.setDeleted(0);
        return report;
    }
}
