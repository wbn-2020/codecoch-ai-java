package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.InterviewReplayCreateDTO;
import com.codecoachai.interview.domain.entity.InterviewReplay;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.support.InterviewReplayEligibilityEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewReplayServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long SOURCE_SESSION_ID = 100L;
    private static final Long SOURCE_REPORT_ID = 88L;

    @Mock
    private InterviewReplayEligibilityEvaluator eligibilityEvaluator;
    @Mock
    private InterviewCloneTransactionService cloneTransactionService;

    private InterviewReplayServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewReplayServiceImpl(
                eligibilityEvaluator,
                cloneTransactionService,
                new ObjectMapper());
        LoginUserContext.setLoginUser(
                LoginUser.builder().userId(USER_ID).username("tester").build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createsReplayThroughOwnedClaimAndFrozenClonePath() {
        InterviewServiceImpl.InterviewClonePreparation preparation =
                clonePreparation();
        when(eligibilityEvaluator.evaluate(USER_ID, SOURCE_SESSION_ID))
                .thenReturn(eligible(9001L));
        when(cloneTransactionService.prepareCloneTarget(
                any(CreateInterviewDTO.class), eq(SOURCE_SESSION_ID)))
                .thenReturn(preparation);
        when(cloneTransactionService.claimReplay(any(InterviewReplay.class)))
                .thenAnswer(invocation -> {
                    InterviewReplay replay = invocation.getArgument(0);
                    replay.setId(600L);
                    replay.setStatus("CREATING");
                    return new InterviewCloneTransactionService.ReplayClaim(
                            replay, "claim-1", true);
                });
        InterviewReplay created = replay(600L, 210L, "CREATED");
        CreateInterviewVO interview = new CreateInterviewVO();
        interview.setId(210L);
        when(cloneTransactionService.createReplayTarget(
                eq(600L), eq("claim-1"), eq(preparation)))
                .thenReturn(new InterviewCloneTransactionService.ReplayCreation(
                        created, interview));

        var result = service.create(
                SOURCE_SESSION_ID, request("replay-1"));

        assertEquals(210L, result.getTargetSessionId());
        assertEquals(9001L, result.getScenarioVersionId());
        assertEquals("CREATED", result.getStatus());
        assertFalse(result.getIdempotentReplay());

        ArgumentCaptor<CreateInterviewDTO> requestCaptor =
                ArgumentCaptor.forClass(CreateInterviewDTO.class);
        verify(cloneTransactionService).prepareCloneTarget(
                requestCaptor.capture(), eq(SOURCE_SESSION_ID));
        CreateInterviewDTO copied = requestCaptor.getValue();
        assertEquals(9001L, copied.getScenarioVersionId());
        assertEquals(300L, copied.getTargetJobId());
        assertEquals("HARD", copied.getDifficulty());
        assertEquals("PRESSURE", copied.getInterviewerStyle());
        assertEquals("MEDIUM", copied.getFollowUpIntensity());
        assertEquals("REPLAY", copied.getPracticeMode());
        assertEquals("INTERVIEW_REPLAY", copied.getRecommendationSource());
        assertEquals("999", copied.getApplicationPackageId());
        assertTrue(copied.getTitle().startsWith("同配置再练："));
    }

    @Test
    void completedReplayIsRecoveredBeforeCurrentSourceEligibilityChecks() {
        InterviewReplay existing = replay(601L, 212L, "CREATED");
        when(cloneTransactionService.recoverCompletedReplay(
                any(InterviewReplay.class)))
                .thenReturn(existing);

        var result = service.create(
                SOURCE_SESSION_ID, request("same-token"));

        assertEquals(212L, result.getTargetSessionId());
        assertTrue(result.getIdempotentReplay());
        verifyNoInteractions(eligibilityEvaluator);
        verify(cloneTransactionService, never())
                .claimReplay(any(InterviewReplay.class));
        verify(cloneTransactionService, never())
                .createReplayTarget(any(), any(), any());
    }

    @Test
    void creationInProgressIsNotReturnedAsSuccessfulReplay() {
        when(eligibilityEvaluator.evaluate(USER_ID, SOURCE_SESSION_ID))
                .thenReturn(eligible(null));
        when(cloneTransactionService.claimReplay(any(InterviewReplay.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "CREATION_IN_PROGRESS"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.create(
                        SOURCE_SESSION_ID, request("busy-token")));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), error.getCode());
        assertEquals("CREATION_IN_PROGRESS", error.getMessage());
        verify(cloneTransactionService, never())
                .createReplayTarget(any(), any(), any());
    }

    @Test
    void targetCreationFailureReleasesClaimForRetry() {
        InterviewServiceImpl.InterviewClonePreparation preparation =
                clonePreparation();
        when(eligibilityEvaluator.evaluate(USER_ID, SOURCE_SESSION_ID))
                .thenReturn(eligible(null));
        when(cloneTransactionService.prepareCloneTarget(
                any(CreateInterviewDTO.class), eq(SOURCE_SESSION_ID)))
                .thenReturn(preparation);
        InterviewReplay claimed = replay(602L, null, "CREATING");
        when(cloneTransactionService.claimReplay(any(InterviewReplay.class)))
                .thenReturn(new InterviewCloneTransactionService.ReplayClaim(
                        claimed, "claim-2", true));
        when(cloneTransactionService.createReplayTarget(
                eq(602L), eq("claim-2"), eq(preparation)))
                .thenThrow(new BusinessException(
                        ErrorCode.SYSTEM_ERROR, "create failed"));

        assertThrows(
                BusinessException.class,
                () -> service.create(
                        SOURCE_SESSION_ID, request("retry-token")));

        verify(cloneTransactionService)
                .releaseReplayClaim(602L, "claim-2");
    }

    @Test
    void optionsExposeIndependentReplayEligibilityContract() {
        when(eligibilityEvaluator.evaluate(USER_ID, SOURCE_SESSION_ID))
                .thenReturn(new InterviewReplayEligibilityEvaluator.Evaluation(
                        session(),
                        report(),
                        null,
                        false,
                        "SCENARIO_VERSION_INVALID",
                        "源场次绑定的场景版本不可用于历史克隆"));

        var result = service.options(SOURCE_SESSION_ID);

        assertEquals("INELIGIBLE", result.getState());
        assertEquals(false, result.getReplayAvailable());
        assertEquals("SCENARIO_VERSION_INVALID", result.getReasonCode());
        assertEquals(
                InterviewReplayEligibilityEvaluator.POLICY_VERSION,
                result.getPolicyVersion());
        assertEquals(SOURCE_REPORT_ID, result.getSourceReportId());
    }

    @Test
    void ineligiblePostUsesTheSameEvaluationAsOptions() {
        InterviewReplayEligibilityEvaluator.Evaluation ineligible =
                new InterviewReplayEligibilityEvaluator.Evaluation(
                        session(),
                        report(),
                        null,
                        false,
                        "REPORT_NOT_GENERATED",
                        "源面试报告尚未生成，不能同配置再练");
        when(eligibilityEvaluator.evaluate(USER_ID, SOURCE_SESSION_ID))
                .thenReturn(ineligible);

        assertThrows(
                BusinessException.class,
                () -> service.create(
                        SOURCE_SESSION_ID, request("no-report")));

        verify(cloneTransactionService, never()).claimReplay(any());
    }

    private InterviewReplayEligibilityEvaluator.Evaluation eligible(
            Long scenarioVersionId) {
        return new InterviewReplayEligibilityEvaluator.Evaluation(
                session(),
                report(),
                scenarioVersionId,
                true,
                null,
                null);
    }

    private InterviewReplayCreateDTO request(String idempotencyKey) {
        InterviewReplayCreateDTO dto = new InterviewReplayCreateDTO();
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private InterviewServiceImpl.InterviewClonePreparation clonePreparation() {
        return new InterviewServiceImpl.InterviewClonePreparation(
                null, session());
    }

    private InterviewReplay replay(Long id, Long targetSessionId, String status) {
        InterviewReplay replay = new InterviewReplay();
        replay.setId(id);
        replay.setUserId(USER_ID);
        replay.setSourceSessionId(SOURCE_SESSION_ID);
        replay.setSourceReportId(SOURCE_REPORT_ID);
        replay.setTargetSessionId(targetSessionId);
        replay.setTargetJobId(300L);
        replay.setScenarioVersionId(9001L);
        replay.setRubricVersion("RUBRIC_ID:5");
        replay.setStatus(status);
        return replay;
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(SOURCE_SESSION_ID);
        session.setUserId(USER_ID);
        session.setDeleted(0);
        session.setMode("TEXT");
        session.setTitle("Java 后端专项");
        session.setTargetJobId(300L);
        session.setApplicationPackageId(999L);
        session.setDifficulty("HARD");
        session.setInterviewerStyle("PRESSURE");
        session.setFollowUpIntensity("MEDIUM");
        session.setMaxQuestionCount(8);
        session.setTargetSkillCodes("[\"REDIS\",\"MQ\"]");
        session.setProjectEvidenceIds("[31,32]");
        return session;
    }

    private InterviewReport report() {
        InterviewReport report = new InterviewReport();
        report.setId(SOURCE_REPORT_ID);
        report.setSessionId(SOURCE_SESSION_ID);
        report.setUserId(USER_ID);
        report.setStatus("GENERATED");
        report.setRubricVersion("RUBRIC_ID:5");
        report.setDeleted(0);
        return report;
    }
}
