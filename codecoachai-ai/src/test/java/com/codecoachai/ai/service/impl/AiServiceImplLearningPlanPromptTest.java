package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.GenerateLearningPlanDTO;
import com.codecoachai.ai.domain.dto.GenerateTargetedStudyPlanDTO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceImplLearningPlanPromptTest {

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
        aiProperties.setMockEnabled(true);
        when(promptRenderService.render(any(String.class), any(String.class), anyMap()))
                .thenAnswer(invocation -> PromptRenderResult.builder()
                        .scene(invocation.getArgument(0))
                        .renderedPrompt("rendered prompt")
                        .inputVariablesJson("{}")
                        .modelParamsJson("{}")
                        .promptHash("hash")
                        .fallbackUsed(true)
                        .build());
        service = new AiServiceImpl(
                aiCallLogMapper,
                promptRenderService,
                aiCallLogService,
                aiProperties,
                new ObjectMapper());
    }

    @Test
    void reportLearningPlanPromptUsesRequestedDailyBudgetAndMinuteConstraint() {
        GenerateLearningPlanDTO dto = reportLearningPlanDTO();
        dto.setDailyMinutes(45);
        service.generateLearningPlan(dto);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(promptRenderService).render(
                eq("LEARNING_PLAN_GENERATE"), prompt.capture(), variables.capture());

        assertEquals("45", variables.getValue().get("dailyMinutes"));
        assertTrue(prompt.getValue().contains("dailyMinutes: {{dailyMinutes}}"));
        assertTrue(prompt.getValue().contains(
                "the sum of estimatedMinutes across all tasks must not exceed dailyMinutes"));
        assertTrue(prompt.getValue().contains("dayOffset"));
        assertTrue(prompt.getValue().contains("estimatedMinutes"));
        assertTrue(prompt.getValue().contains(
                "cover every integer day from 1 through expectedDurationDays without gaps"));
        assertTrue(prompt.getValue().contains("Every day must contain at least one executable task"));
    }

    @Test
    void reportLearningPlanPromptDefaultsDailyBudgetWhenCallerOmitsIt() {
        service.generateLearningPlan(reportLearningPlanDTO());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(promptRenderService).render(
                eq("LEARNING_PLAN_GENERATE"), any(String.class), variables.capture());

        assertEquals("60", variables.getValue().get("dailyMinutes"));
    }

    @Test
    void targetedLearningPlanPromptUsesRequestedDailyBudgetAndSameConstraint() {
        service.generateTargetedStudyPlan(targetedLearningPlanDTO());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(promptRenderService).render(
                eq("TARGETED_STUDY_PLAN_GENERATE"), prompt.capture(), variables.capture());

        assertEquals("45", variables.getValue().get("dailyMinutes"));
        assertTrue(prompt.getValue().contains("dailyMinutes: {{dailyMinutes}}"));
        assertTrue(prompt.getValue().contains(
                "the sum of estimatedMinutes across all tasks must not exceed dailyMinutes"));
        assertTrue(prompt.getValue().contains(
                "cover every integer day from 1 through availableDays without gaps"));
        assertTrue(prompt.getValue().contains("Every day must contain at least one executable task"));
    }

    private GenerateLearningPlanDTO reportLearningPlanDTO() {
        GenerateLearningPlanDTO dto = new GenerateLearningPlanDTO();
        dto.setLearningPlanId(40L);
        dto.setUserId(10L);
        dto.setReportId(30L);
        dto.setTargetPosition("Java backend engineer");
        dto.setInterviewSummary("Needs stronger concurrency and project explanation.");
        dto.setExpectedDurationDays(14);
        return dto;
    }

    private GenerateTargetedStudyPlanDTO targetedLearningPlanDTO() {
        GenerateTargetedStudyPlanDTO dto = new GenerateTargetedStudyPlanDTO();
        dto.setLearningPlanId(60L);
        dto.setUserId(10L);
        dto.setSkillProfileId(70L);
        dto.setTargetJobId(80L);
        dto.setSkillGapsJson("[{\"id\":12,\"skillName\":\"Redis\"}]");
        dto.setAvailableDays(7);
        dto.setDailyMinutes(45);
        dto.setStartDate(LocalDate.of(2026, 8, 17));
        dto.setPlanTitle("Redis repair plan");
        return dto;
    }
}
