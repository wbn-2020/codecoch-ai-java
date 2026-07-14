package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.InterviewComparisonCreateDTO;
import com.codecoachai.interview.domain.entity.InterviewComparison;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.InterviewComparisonVO;
import com.codecoachai.interview.mapper.InterviewComparisonMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.support.InterviewReportComparabilityPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class InterviewComparisonServiceImplTest {

    @Mock
    private InterviewComparisonMapper comparisonMapper;
    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;

    private ObjectMapper objectMapper;
    private InterviewComparisonServiceImpl service;
    private Map<Long, InterviewReport> reports;
    private Map<Long, InterviewSession> sessions;

    @BeforeAll
    static void initTableInfo() {
        init(InterviewComparison.class);
        init(InterviewReport.class);
        init(InterviewSession.class);
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new InterviewComparisonServiceImpl(
                comparisonMapper, reportMapper, sessionMapper, objectMapper,
                new InterviewReportComparabilityPolicy(objectMapper));
        reports = new HashMap<>();
        sessions = new HashMap<>();
        org.mockito.Mockito.lenient().when(reportMapper.selectList(any()))
                .thenAnswer(invocation -> findReports(invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(sessionMapper.selectList(any()))
                .thenAnswer(invocation -> findSessions(invocation.getArgument(0)));
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("tester").build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void comparesSameUserJobAndRubricWithDimensionDeltas() {
        stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));
        when(comparisonMapper.insert(any(InterviewComparison.class))).thenAnswer(invocation -> {
            InterviewComparison comparison = invocation.getArgument(0);
            comparison.setId(900L);
            return 1;
        });

        InterviewComparisonVO result = service.compare(request("compare-success"));

        assertTrue(result.getComparable());
        assertEquals(300L, result.getTargetJobId());
        assertEquals("INTERVIEW_RUBRIC_V1", result.getRubricVersion());
        assertEquals(12, result.getTotalScoreDelta());
        assertEquals(new BigDecimal("2"), result.getDimensions().get(0).getDelta());
        assertEquals(900L, result.getId());
    }

    @Test
    void acceptsDecimalRubricScoresAndPreservesBigDecimalDeltas() {
        InterviewReport first = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        first.setRubricScores("[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":3.5}]");
        InterviewReport second = stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));
        second.setRubricScores("[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":4.0}]");

        InterviewComparisonVO result = service.compare(request("compare-decimal"));

        assertTrue(result.getComparable());
        assertEquals(new BigDecimal("3.5"), result.getRounds().get(0).getRubricScores()
                .get("TECHNICAL_DEPTH"));
        assertEquals(new BigDecimal("4.0"), result.getRounds().get(1).getRubricScores()
                .get("TECHNICAL_DEPTH"));
        assertEquals(new BigDecimal("0.5"), result.getDimensions().get(0).getDelta());
        assertEquals(new BigDecimal("0.5"),
                result.getDimensions().get(0).getPoints().get(1).getDeltaFromPrevious());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedRubricCases")
    void rejectsEntireRubricWhenAnyArrayElementIsInvalid(String caseName, String rubricJson) {
        InterviewReport first = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        first.setRubricScores(rubricJson);
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));

        InterviewComparisonVO result = service.compare(request("compare-malformed-" + caseName));

        assertFalse(result.getComparable());
        assertReason(result, "RUBRIC_DATA_MALFORMED");
        assertEquals(List.of(), result.getDimensions());
    }

    @Test
    void duplicateNormalizedRubricDimensionHasDedicatedReason() {
        InterviewReport first = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        first.setRubricScores("""
                [
                  {"dimension":" technical_depth ","score":3.5},
                  {"dimension":"TECHNICAL_DEPTH","score":4.0}
                ]
                """);
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));

        InterviewComparisonVO result = service.compare(request("compare-duplicate-dimension"));

        assertFalse(result.getComparable());
        assertReason(result, "RUBRIC_DIMENSION_DUPLICATE");
        assertEquals(List.of(), result.getDimensions());
    }

    @Test
    void foreignReportReturnsGenericUnavailableReason() {
        stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        stubReport(12L, 102L, 20L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));

        InterviewComparisonVO result = service.compare(request("compare-user"));

        assertFalse(result.getComparable());
        assertReason(result, "REPORT_UNAVAILABLE");
    }

    @Test
    void returnsTargetJobMismatchReason() {
        stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        stubReport(12L, 102L, 10L, 301L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));

        InterviewComparisonVO result = service.compare(request("compare-job"));

        assertFalse(result.getComparable());
        assertReason(result, "TARGET_JOB_MISMATCH");
    }

    @Test
    void returnsRubricVersionMismatchReason() {
        stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V2", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));

        InterviewComparisonVO result = service.compare(request("compare-rubric"));

        assertFalse(result.getComparable());
        assertReason(result, "RUBRIC_VERSION_MISMATCH");
    }

    @Test
    void missingDimensionInOneReportReturnsDimensionMismatchWithoutTrends() {
        InterviewReport first = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        first.setRubricScores("""
                [
                  {"dimension":"TECHNICAL_DEPTH","score":2},
                  {"dimension":"COMMUNICATION","score":3}
                ]
                """);
        InterviewReport second = stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));
        second.setRubricScores("[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":4}]");

        InterviewComparisonVO result = service.compare(request("compare-dimension-missing"));

        assertFalse(result.getComparable());
        assertReason(result, "RUBRIC_DIMENSION_MISMATCH");
        assertEquals(List.of(), result.getDimensions());
        assertEquals(null, result.getTotalScoreDelta());
    }

    @Test
    void differentDimensionSetsReturnDimensionMismatchWithoutTrends() {
        InterviewReport first = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        first.setRubricScores("""
                [
                  {"dimension":"TECHNICAL_DEPTH","score":2},
                  {"dimension":"COMMUNICATION","score":3}
                ]
                """);
        InterviewReport second = stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));
        second.setRubricScores("""
                [
                  {"dimension":"TECHNICAL_DEPTH","score":4},
                  {"dimension":"PROBLEM_SOLVING","score":5}
                ]
                """);

        InterviewComparisonVO result = service.compare(request("compare-dimension-different"));

        assertFalse(result.getComparable());
        assertReason(result, "RUBRIC_DIMENSION_MISMATCH");
        assertEquals(List.of(), result.getDimensions());
        assertEquals(null, result.getTotalScoreDelta());
    }

    @Test
    void missingTotalScoreIsNotComparableAndDoesNotProduceScoreDeltas() {
        InterviewReport first = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        first.setTotalScore(null);
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));

        InterviewComparisonVO result = service.compare(request("compare-missing-total"));

        assertFalse(result.getComparable());
        assertReason(result, "TOTAL_SCORE_MISSING");
        assertEquals(List.of(), result.getDimensions());
        assertEquals(null, result.getTotalScoreDelta());
    }

    @Test
    void emptyRubricArrayIsNotComparable() {
        InterviewReport first = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        first.setRubricScores("[]");
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));

        InterviewComparisonVO result = service.compare(request("compare-empty-rubric"));

        assertFalse(result.getComparable());
        assertReason(result, "RUBRIC_DATA_MISSING");
    }

    @Test
    void malformedRubricJsonHasExplicitReason() {
        InterviewReport first = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        first.setRubricScores("{not-json");
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));

        InterviewComparisonVO result = service.compare(request("compare-malformed-rubric"));

        assertFalse(result.getComparable());
        assertReason(result, "RUBRIC_DATA_MALFORMED");
    }

    @Test
    void sampleInsufficientReportAddsWeakSignalWarning() {
        InterviewReport first = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        first.setRubricScores("[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":2,\"sampleInsufficient\":true}]");
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));

        InterviewComparisonVO result = service.compare(request("compare-warning"));

        assertTrue(result.getComparable());
        assertEquals("SAMPLE_INSUFFICIENT_REPORT", result.getWarnings().get(0).getCode());
    }

    @Test
    void nullGeneratedAtDoesNotBecomeLatestRound() {
        InterviewReport generated = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));
        generated.setCreatedAt(LocalDateTime.of(2026, 7, 8, 9, 0));
        InterviewReport missingGeneratedAt = stubReport(
                12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2, null);
        missingGeneratedAt.setCreatedAt(LocalDateTime.of(2026, 7, 9, 9, 0));

        InterviewComparisonVO result = service.compare(request("sort-null-generated"));

        assertEquals(List.of(12L, 11L),
                result.getRounds().stream().map(round -> round.getReportId()).toList());
        assertEquals(new BigDecimal("4"), result.getDimensions().get(0).getLatestScore());
    }

    @Test
    void sameGeneratedAtSortsByCreatedAtThenReportId() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 7, 8, 10, 0);
        InterviewReport latestCreated = stubReport(
                11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 84, 5, generatedAt);
        latestCreated.setCreatedAt(LocalDateTime.of(2026, 7, 8, 9, 0));
        InterviewReport firstSameCreated = stubReport(
                12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2, generatedAt);
        firstSameCreated.setCreatedAt(LocalDateTime.of(2026, 7, 7, 9, 0));
        InterviewReport secondSameCreated = stubReport(
                13L, 103L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 75, 3, generatedAt);
        secondSameCreated.setCreatedAt(LocalDateTime.of(2026, 7, 7, 9, 0));

        InterviewComparisonVO result =
                service.compare(request("sort-same-generated", List.of(13L, 11L, 12L)));

        assertEquals(List.of(12L, 13L, 11L),
                result.getRounds().stream().map(round -> round.getReportId()).toList());
        assertEquals(new BigDecimal("5"), result.getDimensions().get(0).getLatestScore());
    }

    @Test
    void idempotentReplayDoesNotReloadReports() throws Exception {
        InterviewComparison existing = storedComparison();
        when(comparisonMapper.selectOne(any())).thenReturn(existing);

        InterviewComparisonVO result = service.compare(request("same-token"));

        assertEquals(900L, result.getId());
        assertTrue(result.getIdempotentReplay());
        verify(reportMapper, never()).selectById(any());
    }

    @Test
    void duplicateKeyRaceReturnsCommittedComparison() throws Exception {
        InterviewComparison concurrent = storedComparison();
        when(comparisonMapper.selectOne(any())).thenReturn(null, concurrent);
        stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));
        when(comparisonMapper.insert(any(InterviewComparison.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        InterviewComparisonVO result = service.compare(request("same-token"));

        assertEquals(900L, result.getId());
        assertTrue(result.getIdempotentReplay());
        ArgumentCaptor<Wrapper<InterviewComparison>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(comparisonMapper, times(2)).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getAllValues().get(1).getSqlSegment().toUpperCase().contains("FOR UPDATE"));
    }

    @Test
    void sameIdempotencyKeyWithDifferentReportsIsRejected() throws Exception {
        InterviewComparison existing = storedComparison();
        existing.setReportIds("[11,13]");
        when(comparisonMapper.selectOne(any())).thenReturn(existing);

        assertThrows(BusinessException.class, () -> service.compare(request("same-token")));

        verify(reportMapper, never()).selectById(any());
    }

    @Test
    void idempotencyReplayRejectsDuplicateRequestAgainstStoredDistinctPayload() throws Exception {
        InterviewComparison existing = storedComparison();
        existing.setReportIds("[11,12]");
        when(comparisonMapper.selectOne(any())).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.compare(request("same-token", List.of(11L, 11L, 12L))));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), exception.getCode());
        verify(reportMapper, never()).selectOne(any());
        verify(reportMapper, never()).selectList(any());
    }

    @Test
    void idempotencyReplayRejectsDistinctRequestAgainstStoredDuplicatePayload() throws Exception {
        InterviewComparison existing = storedComparison();
        existing.setReportIds("[11,11,12]");
        when(comparisonMapper.selectOne(any())).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.compare(request("same-token", List.of(11L, 12L))));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), exception.getCode());
        verify(reportMapper, never()).selectOne(any());
        verify(reportMapper, never()).selectList(any());
    }

    @Test
    void duplicateRequestPersistsSortedIdsWithoutDistinctButAnalyzesDistinctReports() {
        stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));
        ArgumentCaptor<InterviewComparison> comparisonCaptor =
                ArgumentCaptor.forClass(InterviewComparison.class);

        InterviewComparisonVO result =
                service.compare(request("duplicate-persist", List.of(12L, 11L, 11L)));

        verify(comparisonMapper).insert(comparisonCaptor.capture());
        assertEquals("[11,11,12]", comparisonCaptor.getValue().getReportIds());
        assertEquals(List.of(11L, 12L), result.getReportIds());
        assertReason(result, "DUPLICATE_REPORT_ID");
        assertEquals(2, result.getRounds().size());
    }

    @Test
    void listsOwnedComparisonsWithStrictLimit() throws Exception {
        InterviewComparison first = storedComparison();
        InterviewComparison second = storedComparison();
        second.setId(901L);
        when(comparisonMapper.selectList(any())).thenReturn(List.of(second, first));

        List<InterviewComparisonVO> result = service.list(2);

        assertEquals(List.of(901L, 900L), result.stream().map(InterviewComparisonVO::getId).toList());
        assertFalse(result.get(0).getIdempotentReplay());
    }

    @Test
    void rejectsComparisonListLimitOutsideAllowedRange() {
        assertThrows(BusinessException.class, () -> service.list(0));
        assertThrows(BusinessException.class, () -> service.list(51));
        verify(comparisonMapper, never()).selectList(any());
    }

    @Test
    void loadsOwnedComparisonDetail() throws Exception {
        when(comparisonMapper.selectOne(any())).thenReturn(storedComparison());

        InterviewComparisonVO result = service.detail(900L);

        assertEquals(900L, result.getId());
        assertFalse(result.getIdempotentReplay());
    }

    @Test
    void comparisonDetailDoesNotExposeMissingOrForeignSnapshot() {
        when(comparisonMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.detail(900L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void comparisonDetailRejectsInvalidIdAsParameterError() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.detail(null));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void foreignReportUsesGenericUnavailableContractWithoutUnscopedLookup() {
        InterviewReport foreignReport = new InterviewReport();
        foreignReport.setId(11L);
        foreignReport.setSessionId(101L);
        foreignReport.setUserId(20L);
        foreignReport.setDeleted(0);
        reports.put(foreignReport.getId(), foreignReport);

        InterviewComparisonVO result = service.compare(request("foreign-report"));

        assertFalse(result.getComparable());
        assertReason(result, "REPORT_UNAVAILABLE");
        verify(reportMapper, atLeastOnce()).selectList(any());
        verify(reportMapper, never()).selectById(any());
    }

    @Test
    void comparisonReportAndSessionQueriesAreBoundToCurrentUserAndNotDeleted() {
        InterviewReport report = new InterviewReport();
        report.setId(11L);
        report.setSessionId(101L);
        report.setUserId(10L);
        report.setDeleted(0);
        reports.put(report.getId(), report);

        service.compare(request("bound-query"));

        verify(reportMapper, atLeastOnce()).selectList(any());
        verify(sessionMapper, atLeastOnce()).selectList(any());
        verify(reportMapper, never()).selectById(any());
        verify(sessionMapper, never()).selectById(any());
    }

    @Test
    void comparisonAnalysisBatchLoadsOwnedReportsAndSessionsOnce() {
        stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        stubReport(12L, 102L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 82, 4,
                LocalDateTime.of(2026, 7, 8, 10, 0));

        service.compare(request("batch-owned-analysis"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<InterviewReport>> reportQuery = ArgumentCaptor.forClass(Wrapper.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<InterviewSession>> sessionQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(reportMapper).selectList(reportQuery.capture());
        verify(sessionMapper).selectList(sessionQuery.capture());
        verify(reportMapper, never()).selectOne(any());
        verify(sessionMapper, never()).selectOne(any());
        assertOwnerBatchQuery(reportQuery.getValue(), List.of(11L, 12L));
        assertOwnerBatchQuery(sessionQuery.getValue(), List.of(101L, 102L));
    }

    @Test
    void comparabilityGroupContainingNullReturnsReportUnavailable() {
        InterviewReport report = stubReport(11L, 101L, 10L, 300L, "INTERVIEW_RUBRIC_V1", 70, 2,
                LocalDateTime.of(2026, 7, 1, 10, 0));
        InterviewReportComparabilityPolicy policy = new InterviewReportComparabilityPolicy(objectMapper);
        InterviewReportComparabilityPolicy.Result valid = policy.evaluate(report, sessions.get(101L));
        List<InterviewReportComparabilityPolicy.Result> group = new ArrayList<>();
        group.add(valid);
        group.add(null);

        InterviewReportComparabilityPolicy.Result result = policy.evaluateGroup(group);

        assertFalse(result.comparable());
        assertEquals("REPORT_UNAVAILABLE", result.reasonCode());
    }

    private InterviewReport stubReport(
            Long reportId,
            Long sessionId,
            Long userId,
            Long targetJobId,
            String rubricVersion,
            int totalScore,
            int dimensionScore,
            LocalDateTime generatedAt) {
        InterviewReport report = new InterviewReport();
        report.setId(reportId);
        report.setSessionId(sessionId);
        report.setUserId(userId);
        report.setStatus("GENERATED");
        report.setTotalScore(totalScore);
        report.setSummary("报告 " + reportId);
        report.setReportContent("报告正文 " + reportId);
        report.setGeneratedAt(generatedAt);
        report.setRubricVersion(rubricVersion);
        report.setRubricScores("[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":" + dimensionScore
                + ",\"sampleInsufficient\":false}]");

        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setTargetJobId(targetJobId);
        reports.put(reportId, report);
        sessions.put(sessionId, session);
        return report;
    }

    private static Stream<Arguments> malformedRubricCases() {
        return Stream.of(
                Arguments.of("below-range", "[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":0.99}]"),
                Arguments.of("above-range", "[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":5.01}]"),
                Arguments.of("huge-value",
                        "[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":999999999999999999999999999999}]"),
                Arguments.of("non-numeric", "[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":\"NaN\"}]"),
                Arguments.of("missing-dimension", "[{\"score\":4}]"),
                Arguments.of("missing-score", "[{\"dimension\":\"TECHNICAL_DEPTH\"}]"),
                Arguments.of("non-object-element", "[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":4},42]"),
                Arguments.of("blank-dimension", "[{\"dimension\":\"   \",\"score\":4}]"),
                Arguments.of("mixed-valid-invalid",
                        "[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":4},{\"dimension\":\"COMMUNICATION\"}]"));
    }

    private List<InterviewReport> findReports(Object wrapper) {
        List<Object> values = queryValues(wrapper);
        return reports.entrySet().stream()
                .filter(entry -> values.contains(LoginUserContext.getUserId()))
                .filter(entry -> values.contains(0))
                .filter(entry -> LoginUserContext.getUserId().equals(entry.getValue().getUserId()))
                .filter(entry -> !Integer.valueOf(1).equals(entry.getValue().getDeleted()))
                .filter(entry -> values.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private List<InterviewSession> findSessions(Object wrapper) {
        List<Object> values = queryValues(wrapper);
        return sessions.entrySet().stream()
                .filter(entry -> values.contains(LoginUserContext.getUserId()))
                .filter(entry -> values.contains(0))
                .filter(entry -> LoginUserContext.getUserId().equals(entry.getValue().getUserId()))
                .filter(entry -> !Integer.valueOf(1).equals(entry.getValue().getDeleted()))
                .filter(entry -> values.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private List<Object> queryValues(Object wrapper) {
        if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> query) {
            query.getSqlSegment();
            return new ArrayList<>(query.getParamNameValuePairs().values());
        }
        return List.of();
    }

    private void assertOwnerBatchQuery(Wrapper<?> wrapper, List<Long> expectedIds) {
        String sql = wrapper.getSqlSegment().toLowerCase();
        List<Object> values = queryValues(wrapper);
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains(" in "));
        assertTrue(sql.contains("deleted"));
        assertTrue(values.contains(10L));
        assertTrue(values.contains(0));
        assertTrue(values.containsAll(expectedIds));
    }

    private static void init(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }

    private InterviewComparisonCreateDTO request(String token) {
        return request(token, List.of(12L, 11L));
    }

    private InterviewComparisonCreateDTO request(String token, List<Long> reportIds) {
        InterviewComparisonCreateDTO dto = new InterviewComparisonCreateDTO();
        dto.setReportIds(reportIds);
        dto.setIdempotencyKey(token);
        return dto;
    }

    private InterviewComparison storedComparison() throws Exception {
        InterviewComparisonVO result = new InterviewComparisonVO();
        result.setComparable(true);
        result.setReportIds(List.of(11L, 12L));
        result.setUnavailableReasons(new ArrayList<>());
        result.setWarnings(new ArrayList<>());
        result.setRounds(List.of());
        result.setDimensions(List.of());

        InterviewComparison comparison = new InterviewComparison();
        comparison.setId(900L);
        comparison.setUserId(10L);
        comparison.setReportIds("[11,12]");
        comparison.setResultJson(objectMapper.writeValueAsString(result));
        comparison.setIdempotencyKey("same-token");
        return comparison;
    }

    private void assertReason(InterviewComparisonVO result, String code) {
        assertTrue(result.getUnavailableReasons().stream().anyMatch(reason -> code.equals(reason.getCode())));
    }
}
