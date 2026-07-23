package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ResumeApplyAiSuggestionDTO;
import com.codecoachai.resume.domain.dto.JobApplicationEventReviewGenerateDTO;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO;
import com.codecoachai.resume.domain.vo.ResumeSuggestionAdoptionVO;
import com.codecoachai.resume.domain.vo.ResumeVersionVO;
import com.codecoachai.resume.service.V4ResumeCareerService;
import com.codecoachai.resume.service.JobApplicationEventReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V4ResumeCareerControllerTest {

    private static final long USER_ID = 10L;

    @Mock
    private V4ResumeCareerService v4ResumeCareerService;
    @Mock
    private JobApplicationEventReviewService jobApplicationEventReviewService;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private V4ResumeCareerController controller;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("resume-phase-user")
                .build());
        controller = new V4ResumeCareerController(
                v4ResumeCareerService,
                jobApplicationEventReviewService,
                operationConfirmationGuard);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void rollbackVersionRejectsMissingConfirmationBeforeServiceCall() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("RESUME_VERSION_ROLLBACK:10:1:2"),
                isNull(),
                eq(false),
                isNull(),
                isNull()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class,
                () -> controller.rollbackVersion(1L, 2L, null, false, null, null));

        verify(v4ResumeCareerService, never()).rollbackVersion(any(), any());
    }

    @Test
    void rollbackVersionReleasesConfirmationLockWhenServiceFails() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("RESUME_VERSION_ROLLBACK:10:1:2"),
                eq(true),
                eq(false),
                eq("resume rollback reason"),
                eq("resume-rollback-1234")))
                .thenReturn("rollback-lock");
        when(v4ResumeCareerService.rollbackVersion(1L, 2L))
                .thenThrow(new IllegalStateException("rollback failed"));

        assertThrows(IllegalStateException.class,
                () -> controller.rollbackVersion(1L, 2L, true, false,
                        "resume rollback reason", "resume-rollback-1234"));

        verify(operationConfirmationGuard).release("rollback-lock");
    }

    @Test
    void rollbackVersionUsesConfirmedIdempotencyKey() {
        ResumeVersionVO version = new ResumeVersionVO();
        version.setId(2L);
        when(operationConfirmationGuard.requireConfirmed(
                eq("RESUME_VERSION_ROLLBACK:10:1:2"),
                eq(true),
                eq(false),
                eq("resume rollback reason"),
                eq("resume-rollback-1234")))
                .thenReturn("rollback-lock");
        when(v4ResumeCareerService.rollbackVersion(1L, 2L)).thenReturn(version);

        ResumeVersionVO result = controller.rollbackVersion(1L, 2L, true, false,
                "resume rollback reason", "resume-rollback-1234").getData();

        assertEquals(2L, result.getId());
        verify(v4ResumeCareerService).rollbackVersion(1L, 2L);
    }

    @Test
    void applyAiSuggestionRejectsMissingConfirmationBeforeServiceCall() {
        ResumeApplyAiSuggestionDTO dto = new ResumeApplyAiSuggestionDTO();

        when(operationConfirmationGuard.requireConfirmed(
                eq("RESUME_VERSION_APPLY_AI_SUGGESTION:10:2"),
                isNull(),
                isNull(),
                isNull(),
                isNull()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class, () -> controller.applyAiSuggestion(2L, dto));

        verify(v4ResumeCareerService, never()).applyAiSuggestion(any(), any());
    }

    @Test
    void applyAiSuggestionReleasesConfirmationLockWhenServiceFails() {
        ResumeApplyAiSuggestionDTO dto = confirmedSuggestionDto();
        when(operationConfirmationGuard.requireConfirmed(
                eq("RESUME_VERSION_APPLY_AI_SUGGESTION:10:2"),
                eq(true),
                eq(false),
                eq("resume apply ai suggestion"),
                eq("resume-apply-1234")))
                .thenReturn("apply-lock");
        when(v4ResumeCareerService.applyAiSuggestion(2L, dto))
                .thenThrow(new IllegalStateException("apply failed"));

        assertThrows(IllegalStateException.class, () -> controller.applyAiSuggestion(2L, dto));

        verify(operationConfirmationGuard).release("apply-lock");
    }

    @Test
    void applyAiSuggestionUsesConfirmedIdempotencyKey() {
        ResumeApplyAiSuggestionDTO dto = confirmedSuggestionDto();
        ResumeSuggestionAdoptionVO adoption = new ResumeSuggestionAdoptionVO();
        adoption.setId(9L);
        when(operationConfirmationGuard.requireConfirmed(
                eq("RESUME_VERSION_APPLY_AI_SUGGESTION:10:2"),
                eq(true),
                eq(false),
                eq("resume apply ai suggestion"),
                eq("resume-apply-1234")))
                .thenReturn("apply-lock");
        when(v4ResumeCareerService.applyAiSuggestion(2L, dto)).thenReturn(adoption);

        ResumeSuggestionAdoptionVO result = controller.applyAiSuggestion(2L, dto).getData();

        assertEquals(9L, result.getId());
        verify(v4ResumeCareerService).applyAiSuggestion(2L, dto);
    }

    @Test
    void generateApplicationEventReviewDelegatesOwnedPathAndRequest() {
        JobApplicationEventReviewGenerateDTO request = new JobApplicationEventReviewGenerateDTO();
        request.setObservedFacts(java.util.List.of("我确认收到了一封拒信。"));
        JobApplicationEventStructuredReviewVO review =
                new JobApplicationEventStructuredReviewVO();
        review.setScenario("REJECTION");
        when(jobApplicationEventReviewService.generate(8L, 9L, request)).thenReturn(review);

        JobApplicationEventStructuredReviewVO result =
                controller.generateApplicationEventReview(8L, 9L, request).getData();

        assertEquals("REJECTION", result.getScenario());
        verify(jobApplicationEventReviewService).generate(8L, 9L, request);
    }

    private static ResumeApplyAiSuggestionDTO confirmedSuggestionDto() {
        ResumeApplyAiSuggestionDTO dto = new ResumeApplyAiSuggestionDTO();
        dto.setOptimizeRecordId(100L);
        dto.setSuggestionType("AI_RESUME_VERSION");
        dto.setStatus("APPLIED");
        dto.setNote("apply note");
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("resume apply ai suggestion");
        dto.setIdempotencyKey("resume-apply-1234");
        return dto;
    }
}
