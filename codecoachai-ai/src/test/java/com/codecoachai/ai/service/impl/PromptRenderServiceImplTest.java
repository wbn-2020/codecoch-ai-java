package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.domain.enums.PromptVersionStatus;
import com.codecoachai.ai.mapper.PromptTemplateMapper;
import com.codecoachai.ai.mapper.PromptTemplateVersionMapper;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.common.core.constant.CommonConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromptRenderServiceImplTest {

    @Mock
    private PromptTemplateMapper promptTemplateMapper;
    @Mock
    private PromptTemplateVersionMapper promptTemplateVersionMapper;

    private PromptRenderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromptRenderServiceImpl(
                promptTemplateMapper, promptTemplateVersionMapper, new ObjectMapper());
    }

    @ParameterizedTest(name = "{0} renders {1}")
    @MethodSource("businessPromptSentinels")
    void activeDatabasePromptRendersBusinessSentinelIntoFinalPrompt(String scene, String variableName) {
        PromptTemplate template = new PromptTemplate();
        template.setId(10L);
        template.setScene(scene);
        template.setActiveVersionId(20L);
        template.setStatus(CommonConstants.YES);
        template.setEnabled(CommonConstants.YES);

        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setId(20L);
        version.setTemplateId(10L);
        version.setScene(scene);
        version.setVersionCode("v4-054-business-context");
        version.setContent(scene + " business context: {{" + variableName + "}}");
        version.setVariablesJson(variableName);
        version.setStatus(PromptVersionStatus.ACTIVE.name());
        version.setIsActive(CommonConstants.YES);

        when(promptTemplateMapper.selectOne(any())).thenReturn(template);
        when(promptTemplateVersionMapper.selectById(20L)).thenReturn(version);

        String sentinel = "SENTINEL_" + scene;
        PromptRenderResult result = service.render(scene, "fallback", Map.of(variableName, sentinel));

        assertTrue(result.getRenderedPrompt().contains(sentinel));
        assertFalse(result.getRenderedPrompt().contains("{{" + variableName + "}}"));
        assertEquals(20L, result.getPromptTemplateVersionId());
        assertEquals("v4-054-business-context", result.getPromptVersion());
        assertFalse(result.getFallbackUsed());
    }

    private static Stream<Arguments> businessPromptSentinels() {
        return Stream.of(
                Arguments.of("INTERVIEW_QUESTION_GENERATE", "questionContent"),
                Arguments.of("PROJECT_DEEP_DIVE_QUESTION", "projectContent"),
                Arguments.of("INTERVIEW_ANSWER_EVALUATE", "userAnswer"),
                Arguments.of("INTERVIEW_FOLLOW_UP_GENERATE", "aiComment"),
                Arguments.of("INTERVIEW_REPORT_GENERATE", "skillGapContext"),
                Arguments.of("PRACTICE_ANSWER_REVIEW", "userAnswer"),
                Arguments.of("RESUME_JOB_MATCH", "resumeVersionId"),
                Arguments.of("SKILL_GAP_ANALYZE", "matchReportJson"),
                Arguments.of("LEARNING_PLAN_GENERATE", "weaknessSummary"),
                Arguments.of("TARGETED_STUDY_PLAN_GENERATE", "skillGapsJson"),
                Arguments.of("TARGETED_QUESTION_RECOMMEND", "studyTasksJson")
        );
    }
}
