package com.codecoachai.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.core.local.LocalResultMapper;
import com.codecoachai.core.local.LocalTaskInterviewFeignClient;
import com.codecoachai.core.local.LocalTaskQuestionFeignClient;
import com.codecoachai.core.local.LocalTaskResumeFeignClient;
import com.codecoachai.interview.controller.InnerInterviewReportController;
import com.codecoachai.interview.service.StudyPlanService;
import com.codecoachai.question.controller.InnerQuestionController;
import com.codecoachai.question.service.QuestionRecommendationService;
import com.codecoachai.resume.controller.InnerResumeAnalysisController;
import com.codecoachai.resume.service.ResumeJobMatchService;
import com.codecoachai.resume.service.ResumeService;
import com.codecoachai.resume.service.TargetJobService;
import com.codecoachai.resume.service.V4ResumeCareerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class LocalTaskFeignClientBusinessFailureTest {

    private final LocalResultMapper resultMapper = new LocalResultMapper(
            new ObjectMapper(),
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void interviewBusinessFailureBecomesAResultInsteadOfEscapingToMqRetry() {
        InnerInterviewReportController interviewController = mock(InnerInterviewReportController.class);
        when(interviewController.getReportContext(42L))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "session not found"));
        LocalTaskInterviewFeignClient client = new LocalTaskInterviewFeignClient(
                interviewController,
                mock(StudyPlanService.class),
                resultMapper);

        assertBusinessFailure(client.getReportContext(42L), "session not found");
    }

    @Test
    void questionBusinessFailureBecomesAResultInsteadOfEscapingToMqRetry() {
        InnerQuestionController questionController = mock(InnerQuestionController.class);
        when(questionController.saveDrafts(any(InnerQuestionController.SaveQuestionDraftsDTO.class)))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "draft batch invalid"));
        LocalTaskQuestionFeignClient client = new LocalTaskQuestionFeignClient(
                questionController,
                mock(QuestionRecommendationService.class),
                resultMapper);

        assertBusinessFailure(
                client.saveDrafts(new com.codecoachai.task.feign.dto.SaveQuestionDraftsDTO()),
                "draft batch invalid");
    }

    @Test
    void resumeBusinessFailureBecomesAResultInsteadOfEscapingToMqRetry() {
        InnerResumeAnalysisController resumeAnalysisController = mock(InnerResumeAnalysisController.class);
        when(resumeAnalysisController.getAnalysisRawForTask(42L))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "analysis not found"));
        LocalTaskResumeFeignClient client = new LocalTaskResumeFeignClient(
                resumeAnalysisController,
                mock(ResumeJobMatchService.class),
                mock(ResumeService.class),
                mock(TargetJobService.class),
                mock(V4ResumeCareerService.class),
                resultMapper);

        assertBusinessFailure(client.getAnalysisRaw(42L), "analysis not found");
    }

    @Test
    void recommendationServiceBusinessFailureBecomesAResultInsteadOfEscapingToMqRetry() {
        QuestionRecommendationService recommendationService = mock(QuestionRecommendationService.class);
        when(recommendationService.executeBatch(eq(42L), eq(7L)))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "recommendation batch invalid"));
        LocalTaskQuestionFeignClient client = new LocalTaskQuestionFeignClient(
                mock(InnerQuestionController.class),
                recommendationService,
                resultMapper);
        com.codecoachai.task.feign.dto.ExecuteQuestionRecommendationDTO dto =
                new com.codecoachai.task.feign.dto.ExecuteQuestionRecommendationDTO();
        dto.setUserId(7L);

        assertBusinessFailure(
                client.executeRecommendation(42L, dto),
                "recommendation batch invalid");
    }

    private static void assertBusinessFailure(Result<?> result, String message) {
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), result.getCode());
        assertEquals(message, result.getMessage());
    }
}
