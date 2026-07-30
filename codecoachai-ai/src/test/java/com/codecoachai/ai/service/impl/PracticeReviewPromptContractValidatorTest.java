package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.service.PromptSceneContracts;
import org.junit.jupiter.api.Test;

class PracticeReviewPromptContractValidatorTest {

    private static final String VARIABLES =
            "recordId,userId,questionId,questionTitle,questionContent,questionType,difficulty,"
                    + "technologyStack,knowledgePoint,referenceAnswer,analysis,userAnswer,"
                    + "answerDurationSeconds,targetPosition,experienceLevel";

    @Test
    void acceptsManagedPracticeReviewPrompt() {
        var compatibility = PromptSceneContractValidator.evaluate(
                PromptSceneContracts.PRACTICE_ANSWER_REVIEW_SCENE,
                PromptSceneContracts.PRACTICE_ANSWER_REVIEW_VERSION,
                compatibleContent(),
                VARIABLES);

        assertTrue(compatibility.compatible(), compatibility::reason);
    }

    @Test
    void rejectsLegacyPracticeReviewPromptWithoutOutputContract() {
        var compatibility = PromptSceneContractValidator.evaluate(
                PromptSceneContracts.PRACTICE_ANSWER_REVIEW_SCENE,
                "v4-054-business-context",
                "Review {{questionTitle}} and {{userAnswer}}. Output JSON.",
                "questionTitle,userAnswer");

        assertFalse(compatibility.compatible());
    }

    @Test
    void runtimeContractDeclaresStrictResponseAndRelevanceRules() {
        var contract = PromptSceneContracts.practiceReview();

        assertEquals(9, contract.responseFields().size());
        assertEquals(
                java.util.Set.of(
                        "score",
                        "level",
                        "summary",
                        "strengths",
                        "weaknesses",
                        "improvementSuggestions",
                        "referenceComparison",
                        "knowledgeGaps",
                        "suggestedFollowUps"),
                contract.responseFields());
        assertEquals(
                java.util.Set.of(
                        "strengths",
                        "weaknesses",
                        "improvementSuggestions",
                        "knowledgeGaps",
                        "suggestedFollowUps"),
                contract.arrayFields());
        assertEquals(
                java.util.Set.of("level", "summary", "referenceComparison"),
                contract.textFields());
        assertEquals(contract.textFields(), contract.nonEmptyTextFields());
        assertEquals("EXCELLENT", contract.levelMappings().get("优秀"));
        assertEquals("GOOD", contract.levelMappings().get("良好"));
        assertEquals("NORMAL", contract.levelMappings().get("一般"));
        assertEquals("WEAK", contract.levelMappings().get("薄弱"));
        assertEquals(java.util.Set.of("questionTitle", "knowledgePoint"), contract.primaryAnchorFields());
        assertEquals(
                java.util.Set.of("questionContent", "referenceAnswer"),
                contract.independentSupportFields());
        assertEquals(0, contract.minimumScore());
        assertEquals(100, contract.maximumScore());
        assertTrue(contract.jsonObjectRequired());
        assertTrue(contract.exactResponseFields());
        assertTrue(contract.scoreAuthoritative());
        assertTrue(contract.integralScoreRequired());
        assertTrue(contract.nonEmptyArrayElementsRequired());
        assertFalse(contract.realModeHeuristicFallbackAllowed());
        assertTrue(contract.primaryAnchorRequired());
        assertTrue(contract.independentSupportRequired());
        assertFalse(contract.sharedGenericPhraseOnlyAllowed());
        assertEquals(1, contract.maxContributionPerOriginalPhrase());
        assertEquals("WEAK", contract.levelForScore(0));
        assertEquals("NORMAL", contract.levelForScore(60));
        assertEquals("GOOD", contract.levelForScore(75));
        assertEquals("EXCELLENT", contract.levelForScore(90));
        assertEquals("NOT_MASTERED", contract.masteryStatusForScore(59));
        assertEquals("FAMILIAR", contract.masteryStatusForScore(60));
        assertEquals("MASTERED", contract.masteryStatusForScore(80));
    }

    private String compatibleContent() {
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
}
