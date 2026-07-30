package com.codecoachai.ai.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class PromptSceneContracts {

    public static final String JOB_COACH_DAILY_PLAN_SCENE = "JOB_COACH_DAILY_PLAN";
    public static final String JOB_COACH_DAILY_PLAN_VERSION = "v13-agent-skill-gap-context";
    public static final String PRACTICE_ANSWER_REVIEW_SCENE = "PRACTICE_ANSWER_REVIEW";
    public static final String PRACTICE_ANSWER_REVIEW_VERSION = "v4-104-practice-review-contract";

    private static final PracticeReviewContract PRACTICE_REVIEW_CONTRACT = new PracticeReviewContract(
            Set.of(
                    "score",
                    "level",
                    "summary",
                    "strengths",
                    "weaknesses",
                    "improvementSuggestions",
                    "referenceComparison",
                    "knowledgeGaps",
                    "suggestedFollowUps"),
            Set.of(
                    "strengths",
                    "weaknesses",
                    "improvementSuggestions",
                    "knowledgeGaps",
                    "suggestedFollowUps"),
            Set.of("level", "summary", "referenceComparison"),
            Set.of("level", "summary", "referenceComparison"),
            Map.ofEntries(
                    Map.entry("EXCELLENT", "EXCELLENT"),
                    Map.entry("GOOD", "GOOD"),
                    Map.entry("NORMAL", "NORMAL"),
                    Map.entry("WEAK", "WEAK"),
                    Map.entry("优秀", "EXCELLENT"),
                    Map.entry("优异", "EXCELLENT"),
                    Map.entry("卓越", "EXCELLENT"),
                    Map.entry("良好", "GOOD"),
                    Map.entry("较好", "GOOD"),
                    Map.entry("不错", "GOOD"),
                    Map.entry("一般", "NORMAL"),
                    Map.entry("普通", "NORMAL"),
                    Map.entry("中等", "NORMAL"),
                    Map.entry("尚可", "NORMAL"),
                    Map.entry("较弱", "WEAK"),
                    Map.entry("薄弱", "WEAK"),
                    Map.entry("不足", "WEAK"),
                    Map.entry("待提升", "WEAK"),
                    Map.entry("需改进", "WEAK"),
                    Map.entry("不合格", "WEAK")),
            Set.of("questionTitle", "knowledgePoint"),
            Set.of("questionContent", "referenceAnswer"),
            0,
            100,
            true,
            true,
            true,
            true,
            true,
            false,
            true,
            true,
            false,
            1);

    private static final PromptSceneContract JOB_COACH_DAILY_PLAN = new PromptSceneContract(
            JOB_COACH_DAILY_PLAN_SCENE,
            JOB_COACH_DAILY_PLAN_VERSION,
            Set.of("contextJson", "candidatesJson", "taskCount", "maxTotalMinutes"),
            Set.of("SKILL_GAP_ITEM"),
            "");

    private static final PromptSceneContract PRACTICE_ANSWER_REVIEW = new PromptSceneContract(
            PRACTICE_ANSWER_REVIEW_SCENE,
            PRACTICE_ANSWER_REVIEW_VERSION,
            Set.of(
                    "recordId",
                    "userId",
                    "questionId",
                    "questionTitle",
                    "questionContent",
                    "questionType",
                    "difficulty",
                    "technologyStack",
                    "knowledgePoint",
                    "referenceAnswer",
                    "analysis",
                    "userAnswer",
                    "answerDurationSeconds",
                    "targetPosition",
                    "experienceLevel"),
            Set.of(
                    "score 必须是 0 到 100 的整数",
                    "level 只能是 EXCELLENT、GOOD、NORMAL、WEAK",
                    "所有面向用户的文本必须使用正式中文",
                    "summary 必须",
                    "strengths、weaknesses、improvementSuggestions、knowledgeGaps、suggestedFollowUps 必须是字符串数组",
                    "referenceComparison 必须",
                    "不得增加或遗漏固定字段",
                    "不得把数组输出为字符串",
                    "只输出一个合法 JSON 对象",
                    "顶层字段固定为：score, level, summary, strengths, weaknesses, improvementSuggestions, referenceComparison, knowledgeGaps, suggestedFollowUps",
                    "score 的 JSON 类型必须是整数",
                    "缺失、字符串、浮点数或越界值均视为失败",
                    "真实模式不得使用答案长度启发式",
                    "最终 level 和 masteryStatus 以 score 为唯一权威",
                    "每个数组元素必须是非空字符串",
                    "同一原始词组的重叠片段最多贡献一次",
                    "questionTitle 或 knowledgePoint 的主锚点",
                    "questionContent 或 referenceAnswer 提供的第二个独立支撑",
                    "只命中多个输入共享的通用短语视为不相关"),
            PRACTICE_REVIEW_CONTRACT.enforcedPromptSuffix());

    private static final List<PromptSceneContract> CONTRACT_LIST =
            List.of(JOB_COACH_DAILY_PLAN, PRACTICE_ANSWER_REVIEW);

    private static final Map<String, PromptSceneContract> CONTRACTS = Map.of(
            JOB_COACH_DAILY_PLAN.scene(), JOB_COACH_DAILY_PLAN,
            PRACTICE_ANSWER_REVIEW.scene(), PRACTICE_ANSWER_REVIEW);

    private PromptSceneContracts() {
    }

    public static Optional<PromptSceneContract> find(String scene) {
        return Optional.ofNullable(CONTRACTS.get(scene));
    }

    public static Collection<PromptSceneContract> all() {
        return CONTRACT_LIST;
    }

    public static PracticeReviewContract practiceReview() {
        return PRACTICE_REVIEW_CONTRACT;
    }

    public record PromptSceneContract(
            String scene,
            String managedVersionPrefix,
            Set<String> requiredVariables,
            Set<String> requiredContentFragments,
            String enforcedPromptSuffix) {

        public PromptSceneContract {
            requiredVariables = Set.copyOf(requiredVariables);
            requiredContentFragments = Set.copyOf(requiredContentFragments);
            enforcedPromptSuffix = enforcedPromptSuffix == null ? "" : enforcedPromptSuffix;
        }

        public PromptSceneContract(
                String scene,
                String managedVersionPrefix,
                Set<String> requiredVariables,
                Set<String> requiredContentFragments) {
            this(scene, managedVersionPrefix, requiredVariables, requiredContentFragments, "");
        }

        public boolean acceptsVersionCode(String versionCode) {
            if (!StringUtils.hasText(versionCode)) {
                return false;
            }
            return managedVersionPrefix.equals(versionCode)
                    || versionCode.startsWith(managedVersionPrefix + "-");
        }
    }

    public record PracticeReviewContract(
            Set<String> responseFields,
            Set<String> arrayFields,
            Set<String> textFields,
            Set<String> nonEmptyTextFields,
            Map<String, String> levelMappings,
            Set<String> primaryAnchorFields,
            Set<String> independentSupportFields,
            int minimumScore,
            int maximumScore,
            boolean jsonObjectRequired,
            boolean exactResponseFields,
            boolean integralScoreRequired,
            boolean nonEmptyArrayElementsRequired,
            boolean scoreAuthoritative,
            boolean realModeHeuristicFallbackAllowed,
            boolean primaryAnchorRequired,
            boolean independentSupportRequired,
            boolean sharedGenericPhraseOnlyAllowed,
            int maxContributionPerOriginalPhrase) {

        public PracticeReviewContract {
            responseFields = Set.copyOf(responseFields);
            arrayFields = Set.copyOf(arrayFields);
            textFields = Set.copyOf(textFields);
            nonEmptyTextFields = Set.copyOf(nonEmptyTextFields);
            levelMappings = Map.copyOf(levelMappings);
            primaryAnchorFields = Set.copyOf(primaryAnchorFields);
            independentSupportFields = Set.copyOf(independentSupportFields);
        }

        public String enforcedPromptSuffix() {
            return """
                    不可覆盖的练习点评响应合同：
                    1. 真实 AI 响应必须是一个合法 JSON object。顶层字段只能且必须是：
                       score, level, summary, strengths, weaknesses, improvementSuggestions,
                       referenceComparison, knowledgeGaps, suggestedFollowUps。
                    2. score 的 JSON 类型必须是整数且范围为 0 到 100；缺失、字符串、浮点数或越界值均视为失败。
                       真实模式不得使用答案长度启发式、Mock 数据或其他回退分数。
                    3. level 仅允许 EXCELLENT、GOOD、NORMAL、WEAK，或既定中文映射：
                       优秀/优异/卓越=EXCELLENT，良好/较好/不错=GOOD，
                       一般/普通/中等/尚可=NORMAL，较弱/薄弱/不足/待提升/需改进/不合格=WEAK。
                       最终 level 和 masteryStatus 以 score 为唯一权威：
                       90-100=EXCELLENT，75-89=GOOD，60-74=NORMAL，0-59=WEAK；
                       80-100=MASTERED，60-79=FAMILIAR，0-59=NOT_MASTERED。
                    4. summary、referenceComparison 必须是非空字符串；
                       strengths、weaknesses、improvementSuggestions、knowledgeGaps、suggestedFollowUps
                       必须是数组，且每个数组元素必须是非空字符串。
                    5. 中文相关性按输入中的原始词组分组，同一原始词组的重叠片段最多贡献一次。
                       点评必须命中 questionTitle 或 knowledgePoint 的主锚点，
                       并命中 questionContent 或 referenceAnswer 提供的第二个独立支撑；
                       只命中多个输入共享的通用短语视为不相关。
                    """;
        }

        public String levelForScore(int score) {
            requireScoreInRange(score);
            if (score >= 90) {
                return "EXCELLENT";
            }
            if (score >= 75) {
                return "GOOD";
            }
            if (score >= 60) {
                return "NORMAL";
            }
            return "WEAK";
        }

        public String masteryStatusForScore(int score) {
            requireScoreInRange(score);
            if (score >= 80) {
                return "MASTERED";
            }
            if (score >= 60) {
                return "FAMILIAR";
            }
            return "NOT_MASTERED";
        }

        private void requireScoreInRange(int score) {
            if (score < minimumScore || score > maximumScore) {
                throw new IllegalArgumentException(
                        "practice review score must be between " + minimumScore + " and " + maximumScore);
            }
        }
    }
}
