package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.codecoachai.interview.scenario.InterviewScenarioBindingResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewRemediationServiceImplTest {

    @Mock
    private InterviewRemediationMapper remediationMapper;
    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewScenarioBindingResolver bindingResolver;
    @Mock
    private InterviewCloneTransactionService cloneTransactionService;

    private InterviewRemediationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewRemediationServiceImpl(
                remediationMapper, reportMapper, sessionMapper, bindingResolver,
                cloneTransactionService, new ObjectMapper());
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
        verify(cloneTransactionService, never())
                .claimRemediation(any(InterviewRemediation.class));
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
        verify(cloneTransactionService, never())
                .claimRemediation(any(InterviewRemediation.class));
    }

    @Test
    void createsInterviewAndPersistsRemediationSourceContext() {
        when(reportMapper.selectById(88L)).thenReturn(report());
        when(sessionMapper.selectById(100L)).thenReturn(session());
        stubOwnedCreation(500L, 200L);

        var result = service.create(request(false, "normal-create"));

        assertEquals(500L, result.getId());
        assertEquals(200L, result.getTargetSessionId());
        assertEquals(List.of(7L, 9L), result.getSourceRequirementIds());
        assertEquals("补强缓存一致性追问", result.getPracticePurpose());
        assertEquals("NORMAL", result.getRemediationStrength());

        ArgumentCaptor<CreateInterviewDTO> requestCaptor = ArgumentCaptor.forClass(CreateInterviewDTO.class);
        verify(cloneTransactionService)
                .prepareCloneTarget(requestCaptor.capture(), eq(100L));
        assertEquals(300L, requestCaptor.getValue().getTargetJobId());
        assertTrue(requestCaptor.getValue().getRecommendationReason().contains("sourceReportId=88"));
        assertTrue(requestCaptor.getValue().getRecommendationReason().contains("sourceRequirementIds=[7, 9]"));
    }

    @Test
    void remediationCarriesSourceScenarioBindingIntoNewInterview() {
        when(reportMapper.selectById(88L)).thenReturn(report());
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(bindingResolver.reusableScenarioVersionId(100L, 10L, true)).thenReturn(9001L);
        stubOwnedCreation(501L, 201L);

        service.create(request(false, "scenario-carry"));

        ArgumentCaptor<CreateInterviewDTO> requestCaptor = ArgumentCaptor.forClass(CreateInterviewDTO.class);
        verify(cloneTransactionService)
                .prepareCloneTarget(requestCaptor.capture(), eq(100L));
        assertEquals(9001L, requestCaptor.getValue().getScenarioVersionId());
    }

    @Test
    void normalRemediationAcceptsFallbackReport() {
        InterviewReport report = report();
        report.setFailureReason("fallback report");
        when(reportMapper.selectById(88L)).thenReturn(report);
        when(sessionMapper.selectById(100L)).thenReturn(session());
        stubOwnedCreation(502L, 202L);

        var result = service.create(request(false, "normal-fallback"));

        assertEquals(202L, result.getTargetSessionId());
        assertEquals("NORMAL", result.getRemediationStrength());
        verify(cloneTransactionService)
                .createRemediationTarget(eq(502L), eq("claim-502"), any());
    }

    @Test
    void completedRemediationIsRecoveredBeforeCurrentSourceChecks() {
        InterviewRemediation existing = remediation();
        when(cloneTransactionService.recoverCompletedRemediation(any()))
                .thenReturn(existing);

        var result = service.create(request(false, "same-token"));

        assertEquals(200L, result.getTargetSessionId());
        assertTrue(result.getIdempotentReplay());
        verify(reportMapper, never()).selectById(any());
        verify(sessionMapper, never()).selectById(any());
        verify(cloneTransactionService, never())
                .claimRemediation(any(InterviewRemediation.class));
        verify(cloneTransactionService, never())
                .createRemediationTarget(any(), any(), any());
    }

    @Test
    void creationInProgressIsNotReturnedAsSuccessfulRemediation() {
        when(reportMapper.selectById(88L)).thenReturn(report());
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(cloneTransactionService.claimRemediation(any()))
                .thenThrow(new BusinessException(
                        com.codecoachai.common.core.enums.ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "CREATION_IN_PROGRESS"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.create(request(false, "same-token")));

        assertEquals("CREATION_IN_PROGRESS", error.getMessage());
        verify(cloneTransactionService, never())
                .createRemediationTarget(any(), any(), any());
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejected() {
        when(reportMapper.selectById(88L)).thenReturn(report());
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(cloneTransactionService.claimRemediation(any()))
                .thenThrow(new BusinessException(
                        com.codecoachai.common.core.enums.ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "幂等键已被不同的复练请求占用"));

        assertThrows(BusinessException.class, () -> service.create(request(false, "same-token")));

        verify(cloneTransactionService, never())
                .createRemediationTarget(any(), any(), any());
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
        assertEquals(true, result.getRemediationAvailable());
        assertEquals(true, result.getStrongRemediationAvailable());
        assertEquals(false, result.getRemediationCreated());
    }

    @Test
    void remediationOptionsAcceptFallbackReportForNormalRemediation() {
        InterviewReport report = report();
        report.setFailureReason("fallback report");
        report.setWeakPoints("[\"缓存一致性边界不清\"]");
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(sessionMapper.selectById(100L)).thenReturn(session());

        var result = service.options(100L);

        assertEquals("FALLBACK", result.getTrustStatus());
        assertEquals(true, result.getRemediationAvailable());
        assertEquals(false, result.getStrongRemediationAvailable());
        assertEquals("REPORT_UNTRUSTED", result.getStrongRemediationUnavailableReason());
        assertTrue(result.getOptions().stream().anyMatch(option ->
                "WEAK_POINT".equals(option.getReasonType())
                        && !option.getStrongRemediation()));
    }

    @Test
    void remediationOptionsExposeCreatedFallbackRemediation() {
        InterviewReport report = report();
        report.setFailureReason("fallback report");
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(remediationMapper.selectPreferredBySourceReport(10L, 88L))
                .thenReturn(remediation());

        var result = service.options(100L);

        assertEquals(true, result.getRemediationCreated());
        assertEquals(500L, result.getRemediationId());
        assertEquals(200L, result.getRemediationTargetSessionId());
        assertEquals("CREATED", result.getRemediationStatus());
        assertEquals(true, result.getRemediationAvailable());
    }

    @Test
    void remediationOptionsReturnStructuredUnavailableStateForUngeneratedReport() {
        InterviewReport report = report();
        report.setStatus("FAILED");
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(sessionMapper.selectById(100L)).thenReturn(session());

        var result = service.options(100L);

        assertEquals(false, result.getRemediationAvailable());
        assertEquals("REPORT_NOT_GENERATED", result.getRemediationUnavailableReason());
        assertEquals(List.of(), result.getOptions());
    }

    @Test
    void remediationOptionsRejectInvalidHistoricalScenarioBinding() {
        when(reportMapper.selectOne(any())).thenReturn(report());
        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(bindingResolver.reusableScenarioVersionId(100L, 10L, true))
                .thenThrow(new BusinessException(
                        com.codecoachai.common.core.enums.ErrorCode.PARAM_ERROR,
                        "invalid scenario"));

        var result = service.options(100L);

        assertEquals(false, result.getRemediationAvailable());
        assertEquals(
                "SCENARIO_VERSION_INVALID",
                result.getRemediationUnavailableReason());
        assertEquals(List.of(), result.getOptions());
    }

    @Test
    void remediationOptionsDoNotReadReportsForAnotherUserSession() {
        InterviewSession foreignSession = session();
        foreignSession.setUserId(11L);
        when(sessionMapper.selectById(100L)).thenReturn(foreignSession);

        assertThrows(BusinessException.class, () -> service.options(100L));

        verify(reportMapper, never()).selectOne(any());
        verify(remediationMapper, never()).selectOne(any());
    }

    @Test
    void createsQuestionFocusedRemediationWithoutRequirementIds() {
        InterviewRemediationCreateDTO dto = request(false, "question-only");
        dto.setSourceRequirementIds(List.of());
        when(reportMapper.selectById(88L)).thenReturn(report());
        when(sessionMapper.selectById(100L)).thenReturn(session());
        stubOwnedCreation(501L, 201L);

        var result = service.create(dto);

        assertEquals(List.of(), result.getSourceRequirementIds());
        assertEquals(201L, result.getTargetSessionId());
    }

    @Test
    void targetCreationFailureReleasesClaimForRetry() {
        when(reportMapper.selectById(88L)).thenReturn(report());
        when(sessionMapper.selectById(100L)).thenReturn(session());
        InterviewServiceImpl.InterviewClonePreparation preparation =
                clonePreparation();
        when(cloneTransactionService.prepareCloneTarget(
                any(CreateInterviewDTO.class), eq(100L)))
                .thenReturn(preparation);
        InterviewRemediation claimed = remediation();
        claimed.setId(503L);
        claimed.setTargetSessionId(null);
        claimed.setStatus("CREATING");
        when(cloneTransactionService.claimRemediation(any()))
                .thenReturn(new InterviewCloneTransactionService.RemediationClaim(
                        claimed, "claim-503", true));
        when(cloneTransactionService.createRemediationTarget(
                eq(503L), eq("claim-503"), any()))
                .thenThrow(new BusinessException(
                        com.codecoachai.common.core.enums.ErrorCode.SYSTEM_ERROR,
                        "create failed"));

        assertThrows(
                BusinessException.class,
                () -> service.create(request(false, "retryable")));

        verify(cloneTransactionService)
                .releaseRemediationClaim(503L, "claim-503");
    }

    private AtomicReference<InterviewRemediation> stubOwnedCreation(
            Long remediationId, Long targetSessionId) {
        AtomicReference<InterviewRemediation> claimedRef = new AtomicReference<>();
        String claimToken = "claim-" + remediationId;
        InterviewServiceImpl.InterviewClonePreparation preparation =
                clonePreparation();
        when(cloneTransactionService.prepareCloneTarget(
                any(CreateInterviewDTO.class), eq(100L)))
                .thenReturn(preparation);
        when(cloneTransactionService.claimRemediation(any()))
                .thenAnswer(invocation -> {
                    InterviewRemediation claimed = invocation.getArgument(0);
                    claimed.setId(remediationId);
                    claimed.setStatus("CREATING");
                    claimedRef.set(claimed);
                    return new InterviewCloneTransactionService.RemediationClaim(
                            claimed, claimToken, true);
                });
        when(cloneTransactionService.createRemediationTarget(
                eq(remediationId), eq(claimToken), eq(preparation)))
                .thenAnswer(invocation -> {
                    InterviewRemediation created = claimedRef.get();
                    created.setTargetSessionId(targetSessionId);
                    created.setStatus("CREATED");
                    CreateInterviewVO interview = new CreateInterviewVO();
                    interview.setId(targetSessionId);
                    return new InterviewCloneTransactionService.RemediationCreation(
                            created, interview);
                });
        return claimedRef;
    }

    private InterviewServiceImpl.InterviewClonePreparation clonePreparation() {
        return new InterviewServiceImpl.InterviewClonePreparation(
                null, session());
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
