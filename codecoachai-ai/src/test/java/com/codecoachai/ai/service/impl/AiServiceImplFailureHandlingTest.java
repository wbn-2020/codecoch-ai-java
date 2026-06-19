package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.GenerateLearningPlanDTO;
import com.codecoachai.ai.domain.dto.GenerateQuestionDraftDTO;
import com.codecoachai.ai.domain.dto.GenerateTargetedStudyPlanDTO;
import com.codecoachai.ai.domain.dto.PracticeReviewDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceImplFailureHandlingTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private PromptRenderService promptRenderService;
    @Mock
    private AiCallLogService aiCallLogService;

    private AiServiceImpl service;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setMockEnabled(false);
        aiProperties.setProvider("openai-compatible");
        aiProperties.setModel("deepseek-chat");
        when(promptRenderService.render(any(String.class), any(String.class), anyMap()))
                .thenAnswer(invocation -> PromptRenderResult.builder()
                        .scene(invocation.getArgument(0))
                        .renderedPrompt("rendered prompt")
                        .inputVariablesJson("{}")
                        .modelParamsJson("{}")
                        .promptHash("hash")
                        .fallbackUsed(false)
                        .build());
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenThrow(new AiProviderException(AiFailureType.TIMEOUT, "provider timeout"));
        service = new AiServiceImpl(
                aiCallLogMapper,
                promptRenderService,
                aiCallLogService,
                aiProperties,
                new ObjectMapper());
    }

    @Test
    void reviewPracticeDoesNotReturnFallbackWhenRealAiFails() {
        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void generateLearningPlanDoesNotReturnFallbackWhenRealAiFails() {
        assertThrows(BusinessException.class, () -> service.generateLearningPlan(learningPlanDTO()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void generateTargetedStudyPlanDoesNotReturnFallbackWhenRealAiFails() {
        assertThrows(BusinessException.class, () -> service.generateTargetedStudyPlan(targetedStudyPlanDTO()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void generateQuestionDraftsDoesNotReturnMockWhenRealAiResponseCannotBeParsed() {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("not-json-from-provider");
        routeResult.setAiCallLogId(909L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        assertThrows(BusinessException.class, () -> service.generateQuestionDrafts(questionDraftDTO()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    private PracticeReviewDTO practiceReviewDTO() {
        PracticeReviewDTO dto = new PracticeReviewDTO();
        dto.setUserId(10L);
        dto.setRecordId(20L);
        dto.setQuestionId(30L);
        dto.setQuestionTitle("Redis cache penetration");
        dto.setQuestionContent("How do you prevent cache penetration?");
        dto.setKnowledgePoint("Redis");
        dto.setAnswerContent("Use parameter validation, null cache, and Bloom filters.");
        return dto;
    }

    private GenerateLearningPlanDTO learningPlanDTO() {
        GenerateLearningPlanDTO dto = new GenerateLearningPlanDTO();
        dto.setLearningPlanId(40L);
        dto.setUserId(10L);
        dto.setReportId(50L);
        dto.setTargetPosition("Java backend engineer");
        dto.setInterviewSummary("Need stronger Redis and transaction answers.");
        dto.setWeaknessSummary("Redis cache penetration and transaction isolation.");
        dto.setExpectedDurationDays(7);
        return dto;
    }

    private GenerateTargetedStudyPlanDTO targetedStudyPlanDTO() {
        GenerateTargetedStudyPlanDTO dto = new GenerateTargetedStudyPlanDTO();
        dto.setLearningPlanId(60L);
        dto.setUserId(10L);
        dto.setSkillProfileId(70L);
        dto.setTargetJobId(80L);
        dto.setSkillGapsJson("[{\"skillName\":\"Redis\",\"severity\":\"HIGH\",\"gapDescription\":\"Cache penetration\"}]");
        dto.setAvailableDays(7);
        dto.setDailyMinutes(60);
        dto.setStartDate(LocalDate.of(2026, 6, 17));
        dto.setPlanTitle("Redis improvement plan");
        return dto;
    }

    private GenerateQuestionDraftDTO questionDraftDTO() {
        GenerateQuestionDraftDTO dto = new GenerateQuestionDraftDTO();
        dto.setBatchId("batch-parse-failure");
        dto.setAdminUserId(10L);
        dto.setTargetPosition("Java backend engineer");
        dto.setTechnologyStack("Java, Spring Cloud, Redis");
        dto.setKnowledgePoint("Redis cache penetration");
        dto.setQuestionType("SHORT_ANSWER");
        dto.setDifficulty("MEDIUM");
        dto.setCount(3);
        return dto;
    }
}
