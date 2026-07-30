package com.codecoachai.interview.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.scenario.InterviewScenarioBindingResolver;
import com.codecoachai.interview.service.InterviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewReplayEligibilityEvaluatorTest {

    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private InterviewScenarioBindingResolver bindingResolver;
    @Mock
    private InterviewService interviewService;

    private InterviewReplayEligibilityEvaluator evaluator;

    @BeforeAll
    static void initTableInfo() {
        init(InterviewSession.class);
        init(InterviewReport.class);
    }

    private static void init(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    entityClass);
        }
    }

    @BeforeEach
    void setUp() {
        evaluator = new InterviewReplayEligibilityEvaluator(
                sessionMapper,
                reportMapper,
                bindingResolver,
                interviewService,
                new ObjectMapper());
    }

    @Test
    void generatedReportAndCloneableHistoricalScenarioAreEligible() {
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(reportMapper.selectOne(any())).thenReturn(report("GENERATED"));
        when(bindingResolver.reusableScenarioVersionId(100L, 10L, true))
                .thenReturn(9001L);

        var result = evaluator.evaluate(10L, 100L);

        assertTrue(result.eligible());
        assertEquals(9001L, result.scenarioVersionId());
        ArgumentCaptor<CreateInterviewDTO> requestCaptor =
                ArgumentCaptor.forClass(CreateInterviewDTO.class);
        verify(interviewService)
                .validateClone(requestCaptor.capture(), eq(100L));
        assertEquals(9001L, requestCaptor.getValue().getScenarioVersionId());
        assertEquals("999", requestCaptor.getValue().getApplicationPackageId());
    }

    @Test
    void ungeneratedReportReturnsStructuredIneligibleReason() {
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(reportMapper.selectOne(any())).thenReturn(report("FAILED"));

        var result = evaluator.evaluate(10L, 100L);

        assertFalse(result.eligible());
        assertEquals("REPORT_NOT_GENERATED", result.reasonCode());
    }

    @Test
    void invalidHistoricalScenarioReturnsStructuredReason() {
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(reportMapper.selectOne(any())).thenReturn(report("GENERATED"));
        when(bindingResolver.reusableScenarioVersionId(100L, 10L, true))
                .thenThrow(new BusinessException(
                        ErrorCode.PARAM_ERROR, "invalid scenario"));

        var result = evaluator.evaluate(10L, 100L);

        assertFalse(result.eligible());
        assertEquals("SCENARIO_VERSION_INVALID", result.reasonCode());
    }

    @Test
    void invalidCloneDependencyReturnsStructuredIneligibleReason() {
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(reportMapper.selectOne(any())).thenReturn(report("GENERATED"));
        when(bindingResolver.reusableScenarioVersionId(100L, 10L, true))
                .thenReturn(9001L);
        doThrow(new BusinessException(
                        ErrorCode.PARAM_ERROR,
                        "求职申请包不存在或不可用"))
                .when(interviewService)
                .validateClone(any(CreateInterviewDTO.class), eq(100L));

        var result = evaluator.evaluate(10L, 100L);

        assertFalse(result.eligible());
        assertEquals("CLONE_CONTEXT_INVALID", result.reasonCode());
        assertEquals("求职申请包不存在或不可用", result.reasonMessage());
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(100L);
        session.setUserId(10L);
        session.setDeleted(0);
        session.setApplicationPackageId(999L);
        return session;
    }

    private InterviewReport report(String status) {
        InterviewReport report = new InterviewReport();
        report.setId(88L);
        report.setSessionId(100L);
        report.setUserId(10L);
        report.setDeleted(0);
        report.setStatus(status);
        return report;
    }
}
