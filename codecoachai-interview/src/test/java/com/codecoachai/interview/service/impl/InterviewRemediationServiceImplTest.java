package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.InterviewRemediationCreateDTO;
import com.codecoachai.interview.domain.entity.InterviewRemediation;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.mapper.InterviewRemediationMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.service.InterviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class InterviewRemediationServiceImplTest {

    @Mock
    private InterviewRemediationMapper remediationMapper;
    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewService interviewService;

    private InterviewRemediationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewRemediationServiceImpl(
                remediationMapper, reportMapper, sessionMapper, interviewService, new ObjectMapper());
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("tester").build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void strongRemediationRejectsUntrustedReport() {
        InterviewReport report = report();
        report.setFailureReason("fallback report");
        when(reportMapper.selectById(88L)).thenReturn(report);
        when(sessionMapper.selectById(100L)).thenReturn(session());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(request(true, "strong-untrusted")));

        assertTrue(error.getMessage().contains("可信度不足"));
        verify(remediationMapper, never()).insert(any(InterviewRemediation.class));
        verify(interviewService, never()).create(any());
    }

    @Test
    void strongRemediationRejectsSampleInsufficientReport() {
        InterviewReport report = report();
        report.setRubricScores("[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":2,\"sampleInsufficient\":true}]");
        when(reportMapper.selectById(88L)).thenReturn(report);
        when(sessionMapper.selectById(100L)).thenReturn(session());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(request(true, "strong-sample")));

        assertTrue(error.getMessage().contains("样本不足"));
        verify(interviewService, never()).create(any());
    }

    @Test
    void createsInterviewAndPersistsRemediationSourceContext() {
        when(reportMapper.selectById(88L)).thenReturn(report());
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(remediationMapper.insert(any(InterviewRemediation.class))).thenAnswer(invocation -> {
            InterviewRemediation remediation = invocation.getArgument(0);
            remediation.setId(500L);
            return 1;
        });
        CreateInterviewVO interview = new CreateInterviewVO();
        interview.setId(200L);
        when(interviewService.create(any())).thenReturn(interview);
        when(sessionMapper.updateById(any(InterviewSession.class))).thenReturn(1);
        when(remediationMapper.updateById(any(InterviewRemediation.class))).thenReturn(1);

        var result = service.create(request(false, "normal-create"));

        assertEquals(500L, result.getId());
        assertEquals(200L, result.getTargetSessionId());
        assertEquals(List.of(7L, 9L), result.getSourceRequirementIds());
        assertEquals("补强缓存一致性追问", result.getPracticePurpose());
        assertEquals("NORMAL", result.getRemediationStrength());

        ArgumentCaptor<CreateInterviewDTO> requestCaptor = ArgumentCaptor.forClass(CreateInterviewDTO.class);
        verify(interviewService).create(requestCaptor.capture());
        assertEquals(300L, requestCaptor.getValue().getTargetJobId());
        assertTrue(requestCaptor.getValue().getRecommendationReason().contains("sourceReportId=88"));
        assertTrue(requestCaptor.getValue().getRecommendationReason().contains("sourceRequirementIds=[7, 9]"));

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionMapper).updateById(sessionCaptor.capture());
        assertEquals(88L, sessionCaptor.getValue().getSourceReportId());
        assertEquals("[7,9]", sessionCaptor.getValue().getSourceRequirementIds());
        assertEquals("补强缓存一致性追问", sessionCaptor.getValue().getPracticePurpose());
    }

    @Test
    void idempotentReplayDoesNotCreateAnotherInterview() {
        InterviewRemediation existing = remediation();
        when(remediationMapper.selectOne(any())).thenReturn(existing);

        var result = service.create(request(false, "same-token"));

        assertEquals(200L, result.getTargetSessionId());
        assertTrue(result.getIdempotentReplay());
        verify(reportMapper, never()).selectById(any());
        verify(interviewService, never()).create(any());
    }

    @Test
    void duplicateKeyRaceReturnsCommittedRemediationWithoutCreatingSecondInterview() {
        InterviewRemediation concurrent = remediation();
        when(remediationMapper.selectOne(any())).thenReturn(null, concurrent);
        when(reportMapper.selectById(88L)).thenReturn(report());
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(remediationMapper.insert(any(InterviewRemediation.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        var result = service.create(request(false, "same-token"));

        assertEquals(200L, result.getTargetSessionId());
        assertTrue(result.getIdempotentReplay());
        verify(interviewService, never()).create(any());
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejected() {
        InterviewRemediation existing = remediation();
        existing.setPracticePurpose("其他目的");
        when(remediationMapper.selectOne(any())).thenReturn(existing);

        assertThrows(BusinessException.class, () -> service.create(request(false, "same-token")));

        verify(interviewService, never()).create(any());
    }

    @Test
    void buildsRemediationOptionsFromTrustedWeaknessesFailedQuestionsAndRequirements() {
        InterviewReport report = report();
        report.setWeakPoints("[\"缓存一致性边界不清\"]");
        report.setQaReview("""
                [{"questionId":21,"questionContent":"缓存更新失败如何补偿？","score":45,
                  "comment":"缺少失败补偿和监控闭环","requirementId":7}]
                """);
        report.setRubricScores("""
                [{"dimension":"TECHNICAL_DEPTH","score":2,
                  "improvementSuggestion":"补充异常路径和回滚策略","requirementIds":[9],
                  "sampleInsufficient":false}]
                """);
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(sessionMapper.selectById(100L)).thenReturn(session());

        var result = service.options(100L);

        assertEquals(88L, result.getSourceReportId());
        assertTrue(result.getOptions().stream().anyMatch(option ->
                "FAILED_QUESTION".equals(option.getReasonType())
                        && option.getSourceRequirementIds().contains(7L)));
        assertTrue(result.getOptions().stream().anyMatch(option ->
                "WEAK_DIMENSION".equals(option.getReasonType())
                        && option.getSourceRequirementIds().contains(9L)));
        assertTrue(result.getOptions().stream().anyMatch(option ->
                "WEAK_POINT".equals(option.getReasonType())
                        && option.getPracticePurpose().contains("缓存一致性")));
    }

    @Test
    void remediationOptionsRejectUntrustedReport() {
        InterviewReport report = report();
        report.setFailureReason("fallback report");
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(sessionMapper.selectById(100L)).thenReturn(session());

        assertThrows(BusinessException.class, () -> service.options(100L));
    }

    @Test
    void createsQuestionFocusedRemediationWithoutRequirementIds() {
        InterviewRemediationCreateDTO dto = request(false, "question-only");
        dto.setSourceRequirementIds(List.of());
        when(reportMapper.selectById(88L)).thenReturn(report());
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(remediationMapper.insert(any(InterviewRemediation.class))).thenAnswer(invocation -> {
            InterviewRemediation remediation = invocation.getArgument(0);
            remediation.setId(501L);
            return 1;
        });
        CreateInterviewVO interview = new CreateInterviewVO();
        interview.setId(201L);
        when(interviewService.create(any())).thenReturn(interview);
        when(sessionMapper.updateById(any(InterviewSession.class))).thenReturn(1);
        when(remediationMapper.updateById(any(InterviewRemediation.class))).thenReturn(1);

        var result = service.create(dto);

        assertEquals(List.of(), result.getSourceRequirementIds());
        assertEquals(201L, result.getTargetSessionId());
    }

    private InterviewRemediationCreateDTO request(boolean strong, String token) {
        InterviewRemediationCreateDTO dto = new InterviewRemediationCreateDTO();
        dto.setSourceReportId(88L);
        dto.setSourceRequirementIds(List.of(9L, 7L, 9L));
        dto.setPracticePurpose("补强缓存一致性追问");
        dto.setStrongRemediation(strong);
        dto.setIdempotencyKey(token);
        return dto;
    }

    private InterviewReport report() {
        InterviewReport report = new InterviewReport();
        report.setId(88L);
        report.setSessionId(100L);
        report.setUserId(10L);
        report.setStatus("GENERATED");
        report.setTotalScore(78);
        report.setSummary("可信报告");
        report.setReportContent("可信报告正文");
        report.setGeneratedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        report.setRubricVersion("INTERVIEW_RUBRIC_V1");
        report.setRubricScores("[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":3,\"sampleInsufficient\":false}]");
        report.setAdviceEvidence("""
                [{"title":"练习缓存一致性","sampleInsufficient":false,
                  "evidenceSources":[{"sourceType":"INTERVIEW_REPORT","sourceId":88,"sourceSummary":"技术深度不足"}]}]
                """);
        return report;
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(100L);
        session.setUserId(10L);
        session.setTargetJobId(300L);
        session.setMode("COMPREHENSIVE");
        session.setTitle("Java 后端模拟面试");
        session.setTargetPosition("Java 后端工程师");
        session.setMaxQuestionCount(5);
        session.setTargetSkillCodes("[\"REDIS\"]");
        session.setProjectEvidenceIds("[]");
        return session;
    }

    private InterviewRemediation remediation() {
        InterviewRemediation remediation = new InterviewRemediation();
        remediation.setId(500L);
        remediation.setUserId(10L);
        remediation.setSourceReportId(88L);
        remediation.setSourceSessionId(100L);
        remediation.setTargetSessionId(200L);
        remediation.setTargetJobId(300L);
        remediation.setSourceRequirementIds("[7,9]");
        remediation.setPracticePurpose("补强缓存一致性追问");
        remediation.setRemediationStrength("NORMAL");
        remediation.setRubricVersion("INTERVIEW_RUBRIC_V1");
        remediation.setStatus("CREATED");
        remediation.setIdempotencyKey("same-token");
        return remediation;
    }
}
