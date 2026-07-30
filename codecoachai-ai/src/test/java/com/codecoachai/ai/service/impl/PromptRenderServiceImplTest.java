package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.domain.enums.PromptVersionStatus;
import com.codecoachai.ai.mapper.PromptTemplateMapper;
import com.codecoachai.ai.mapper.PromptTemplateVersionMapper;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptSceneContracts;
import com.codecoachai.common.core.constant.CommonConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromptRenderServiceImplTest {

    private static final String PRACTICE_REVIEW_VARIABLES =
            "recordId,userId,questionId,questionTitle,questionContent,questionType,difficulty,"
                    + "technologyStack,knowledgePoint,referenceAnswer,analysis,userAnswer,"
                    + "answerDurationSeconds,targetPosition,experienceLevel";

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
        boolean managedPracticeReview =
                PromptSceneContracts.PRACTICE_ANSWER_REVIEW_SCENE.equals(scene);
        String versionCode = managedPracticeReview
                ? PromptSceneContracts.PRACTICE_ANSWER_REVIEW_VERSION
                : "v4-054-business-context";
        version.setVersionCode(versionCode);
        version.setContent(managedPracticeReview
                ? managedPracticeReviewContent()
                : scene + " business context: {{" + variableName + "}}");
        version.setVariablesJson(managedPracticeReview
                ? PRACTICE_REVIEW_VARIABLES
                : variableName);
        version.setStatus(PromptVersionStatus.ACTIVE.name());
        version.setIsActive(CommonConstants.YES);

        when(promptTemplateMapper.selectOne(any())).thenReturn(template);
        when(promptTemplateVersionMapper.selectById(20L)).thenReturn(version);

        String sentinel = "SENTINEL_" + scene;
        Map<String, String> variables = managedPracticeReview
                ? practiceReviewVariables(sentinel)
                : Map.of(variableName, sentinel);
        PromptRenderResult result = service.render(scene, "fallback", variables);

        assertTrue(result.getRenderedPrompt().contains(sentinel));
        assertFalse(result.getRenderedPrompt().contains("{{" + variableName + "}}"));
        assertEquals(20L, result.getPromptTemplateVersionId());
        assertEquals(versionCode, result.getPromptVersion());
        assertFalse(result.getFallbackUsed());
        if (managedPracticeReview) {
            assertTrue(result.getRenderedPrompt().contains("score 的 JSON 类型必须是整数"));
            assertTrue(result.getRenderedPrompt().contains("真实模式不得使用答案长度启发式"));
            assertTrue(result.getRenderedPrompt().contains("最终 level 和 masteryStatus 以 score 为唯一权威"));
            assertTrue(result.getRenderedPrompt().contains("同一原始词组的重叠片段最多贡献一次"));
            assertTrue(result.getRenderedPrompt().contains("第二个独立支撑"));
            assertTrue(result.getRenderedPrompt().contains("只命中多个输入共享的通用短语视为不相关"));
        }
    }

    @Test
    void staleManagedDailyPlanPromptCannotOverrideV13BuiltinPrompt() {
        stubActiveVersion(
                PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE,
                "v4.2-zh-evidence-json",
                "old plan context={{contextJson}} candidates={{candidatesJson}} "
                        + "count={{taskCount}} max={{maxTotalMinutes}}",
                "contextJson,candidatesJson,taskCount,maxTotalMinutes");

        PromptRenderResult result = service.render(
                PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE,
                "builtin V13 SKILL_GAP_ITEM prompt",
                Map.of(
                        "contextJson", "{}",
                        "candidatesJson", "[]",
                        "taskCount", "1",
                        "maxTotalMinutes", "30"));

        assertEquals("builtin V13 SKILL_GAP_ITEM prompt", result.getRenderedPrompt());
        assertTrue(result.getFallbackUsed());
        assertNull(result.getPromptTemplateVersionId());
        assertEquals("BUILTIN", result.getPromptVersion());
    }

    @Test
    void builtinPracticeReviewPromptStillReceivesNonOverridableResponseContract() {
        PromptRenderResult result = service.render(
                PromptSceneContracts.PRACTICE_ANSWER_REVIEW_SCENE,
                "questionTitle={{questionTitle}} userAnswer={{userAnswer}}",
                Map.of("questionTitle", "事务失效", "userAnswer", "自调用绕过代理"));

        assertTrue(result.getFallbackUsed());
        assertTrue(result.getRenderedPrompt().contains("questionTitle=事务失效"));
        assertTrue(result.getRenderedPrompt().contains("score 的 JSON 类型必须是整数"));
        assertTrue(result.getRenderedPrompt().contains("顶层字段只能且必须是"));
        assertTrue(result.getRenderedPrompt().contains("每个数组元素必须是非空字符串"));
        assertTrue(result.getRenderedPrompt().contains("questionTitle 或 knowledgePoint 的主锚点"));
        assertTrue(result.getRenderedPrompt().contains("questionContent 或 referenceAnswer"));
    }

    @Test
    void compatibleManagedDailyPlanPromptCanOverrideBuiltinPrompt() {
        stubActiveVersion(
                PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE,
                PromptSceneContracts.JOB_COACH_DAILY_PLAN_VERSION,
                "SKILL_GAP_ITEM count={{taskCount}} max={{maxTotalMinutes}} "
                        + "context={{contextJson}} candidates={{candidatesJson}}",
                "contextJson,candidatesJson,taskCount,maxTotalMinutes");

        PromptRenderResult result = service.render(
                PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE,
                "builtin",
                Map.of(
                        "contextJson", "{\"skillGaps\":[]}",
                        "candidatesJson", "[]",
                        "taskCount", "2",
                        "maxTotalMinutes", "45"));

        assertTrue(result.getRenderedPrompt().contains("SKILL_GAP_ITEM count=2 max=45"));
        assertFalse(result.getFallbackUsed());
        assertEquals(20L, result.getPromptTemplateVersionId());
        assertEquals(PromptSceneContracts.JOB_COACH_DAILY_PLAN_VERSION, result.getPromptVersion());
    }

    @Test
    void startupContractCheckFailsFastWhenMigrationDidNotPublishV13Prompt() {
        stubActiveVersion(
                PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE,
                "v4.2-zh-evidence-json",
                "old plan context={{contextJson}} candidates={{candidatesJson}} "
                        + "count={{taskCount}} max={{maxTotalMinutes}}",
                "contextJson,candidatesJson,taskCount,maxTotalMinutes");

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> service.verifyActivePromptContracts(true));

        assertTrue(exception.getMessage().contains(PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE));
        assertTrue(exception.getMessage().contains(PromptSceneContracts.JOB_COACH_DAILY_PLAN_VERSION));
    }

    @Test
    void startupContractCheckFailsFastWhenManagedActiveSourceIsMissing() {
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> service.verifyActivePromptContracts(true));

        assertTrue(exception.getMessage().contains("Managed prompt source is missing"));
        assertTrue(exception.getMessage().contains(PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE));
        assertTrue(exception.getMessage().contains(PromptSceneContracts.JOB_COACH_DAILY_PLAN_VERSION));
    }

    @Test
    void nonFailFastMissingManagedSourceAllowsBuiltinFallback() {
        assertDoesNotThrow(() -> service.verifyActivePromptContracts(false));

        PromptRenderResult result = service.render(
                PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE,
                "builtin V13 SKILL_GAP_ITEM prompt",
                Map.of());

        assertEquals("builtin V13 SKILL_GAP_ITEM prompt", result.getRenderedPrompt());
        assertTrue(result.getFallbackUsed());
        assertNull(result.getPromptTemplateVersionId());
        assertEquals("BUILTIN", result.getPromptVersion());
    }

    @Test
    void enabledTemplateFallbackVersionRendersWhenActivePointerIsUnavailable() {
        String scene = "INTERVIEW_ANSWER_EVALUATE";
        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setId(30L);
        version.setTemplateId(11L);
        version.setScene(scene);
        version.setVersionCode("v-fallback");
        version.setContent("Answer={{userAnswer}}");
        version.setVariablesJson("userAnswer");
        version.setStatus(PromptVersionStatus.ACTIVE.name());
        version.setIsActive(CommonConstants.YES);
        when(promptTemplateVersionMapper.selectActiveVersionOwnedByEnabledTemplate(scene)).thenReturn(version);

        PromptRenderResult result = service.render(scene, "builtin", Map.of("userAnswer", "sentinel"));

        assertEquals("Answer=sentinel", result.getRenderedPrompt());
        assertFalse(result.getFallbackUsed());
        assertEquals(30L, result.getPromptTemplateVersionId());
    }

    private void stubActiveVersion(String scene, String versionCode, String content, String variablesJson) {
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
        version.setVersionCode(versionCode);
        version.setContent(content);
        version.setVariablesJson(variablesJson);
        version.setStatus(PromptVersionStatus.ACTIVE.name());
        version.setIsActive(CommonConstants.YES);

        when(promptTemplateMapper.selectOne(any())).thenReturn(template);
        when(promptTemplateVersionMapper.selectById(20L)).thenReturn(version);
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

    private static String managedPracticeReviewContent() {
        return """
                recordId={{recordId}}
                userId={{userId}}
                questionId={{questionId}}
                questionTitle={{questionTitle}}
                questionContent={{questionContent}}
                questionType={{questionType}}
                difficulty={{difficulty}}
                technologyStack={{technologyStack}}
                knowledgePoint={{knowledgePoint}}
                referenceAnswer={{referenceAnswer}}
                analysis={{analysis}}
                userAnswer={{userAnswer}}
                answerDurationSeconds={{answerDurationSeconds}}
                targetPosition={{targetPosition}}
                experienceLevel={{experienceLevel}}
                score 必须是 0 到 100 的整数
                level 只能是 EXCELLENT、GOOD、NORMAL、WEAK
                所有面向用户的文本必须使用正式中文
                summary 必须说明整体表现
                strengths、weaknesses、improvementSuggestions、knowledgeGaps、suggestedFollowUps 必须是字符串数组
                referenceComparison 必须对比关键差异
                不得增加或遗漏固定字段
                不得把数组输出为字符串
                只输出一个合法 JSON 对象
                顶层字段固定为：score, level, summary, strengths, weaknesses, improvementSuggestions, referenceComparison, knowledgeGaps, suggestedFollowUps
                """;
    }

    private static Map<String, String> practiceReviewVariables(String userAnswer) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (String variable : PRACTICE_REVIEW_VARIABLES.split(",")) {
            variables.put(variable, "");
        }
        variables.put("userAnswer", userAnswer);
        return variables;
    }
}
