package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.GenerateTargetedStudyPlanDTO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTargetedStudyPlanPromptTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private PromptRenderService promptRenderService;
    @Mock
    private AiCallLogService aiCallLogService;

    @Test
    void defaultTargetedStudyPlanPromptExplainsEvidenceUsageFeedbackGaps() {
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
        AiServiceImpl service = new AiServiceImpl(
                aiCallLogMapper,
                promptRenderService,
                aiCallLogService,
                aiProperties,
                new ObjectMapper());

        service.generateTargetedStudyPlan(targetedStudyPlanDTO());

        ArgumentCaptor<String> fallbackPrompt = ArgumentCaptor.forClass(String.class);
        verify(promptRenderService).render(
                eq("TARGETED_STUDY_PLAN_GENERATE"), fallbackPrompt.capture(), anyMap());
        String prompt = fallbackPrompt.getValue();
        assertTrue(prompt.contains("{{skillGapsJson}}"),
                "prompt must keep the selected gap payload placeholder");
        assertTrue(prompt.contains("EVIDENCE_USAGE_FEEDBACK"),
                "prompt must explain the evidence-usage feedback gap category");
        assertTrue(prompt.contains("present, and defend the existing evidence"),
                "evidence gaps must steer toward presentation training instead of relearning");
    }

    private GenerateTargetedStudyPlanDTO targetedStudyPlanDTO() {
        GenerateTargetedStudyPlanDTO dto = new GenerateTargetedStudyPlanDTO();
        dto.setLearningPlanId(60L);
        dto.setUserId(10L);
        dto.setSkillProfileId(70L);
        dto.setTargetJobId(80L);
        dto.setSkillGapsJson(
                "[{\"skillName\":\"Redis\",\"category\":\"EVIDENCE_USAGE_FEEDBACK\","
                        + "\"gapDescription\":\"证据《分布式锁改造》在面试中使用后未晋级\"}]");
        dto.setAvailableDays(7);
        dto.setDailyMinutes(60);
        dto.setStartDate(LocalDate.of(2026, 7, 26));
        dto.setPlanTitle("Evidence feedback plan");
        return dto;
    }
}
