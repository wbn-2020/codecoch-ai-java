package com.codecoachai.interview.support;

import com.codecoachai.interview.domain.entity.InterviewReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import org.springframework.util.StringUtils;

/**
 * Minimum persistence contract for a report that can be shown as a completed interview result.
 */
public final class InterviewReportConsumabilityContract {

    public static final String SCHEMA_VERSION = "interview-report-v1";
    public static final int MINIMUM_SCORABLE_ANSWER_COUNT = 6;
    /** 体验版报告门槛（2026-09-11 分层决策）：3-5 条有效回答可出体验版报告，仍需满足其余合同。 */
    public static final int MINIMUM_TRIAL_ANSWER_COUNT = 3;

    /**
     * 体验版（3-5 条有效回答）可用性校验：与正式报告相同的合同，仅把样本量门槛放宽到
     * MINIMUM_TRIAL_ANSWER_COUNT，其余内容、评分与逐题明细校验一律不放宽。
     */
    public static Validation validateTrial(ObjectMapper objectMapper,
                                           InterviewReport report,
                                           int expectedAnswerCount,
                                           String expectedDimensionsJson) {
        if (expectedAnswerCount < MINIMUM_TRIAL_ANSWER_COUNT) {
            return invalid(
                    "ANSWER_EVIDENCE_INSUFFICIENT",
                    "Interview trial report requires at least "
                            + MINIMUM_TRIAL_ANSWER_COUNT
                            + " valid answers but found "
                            + Math.max(expectedAnswerCount, 0));
        }
        return validate(objectMapper, report, expectedAnswerCount, expectedDimensionsJson);
    }

    private InterviewReportConsumabilityContract() {
    }

    public static Validation validate(ObjectMapper objectMapper,
                                      InterviewReport report,
                                      int expectedAnswerCount,
                                      String expectedDimensionsJson) {
        if (report == null) {
            return invalid("REPORT_MISSING", "Interview report is missing");
        }
        if (expectedAnswerCount < MINIMUM_SCORABLE_ANSWER_COUNT) {
            return invalid(
                    "ANSWER_EVIDENCE_INSUFFICIENT",
                    "Interview report requires at least "
                            + MINIMUM_SCORABLE_ANSWER_COUNT
                            + " valid answers but found "
                            + Math.max(expectedAnswerCount, 0));
        }
        InterviewReportScoringContract.Validation scoring = InterviewReportScoringContract.validate(
                objectMapper,
                report.getTotalScore(),
                report.getRubricVersion(),
                report.getRubricScores(),
                expectedDimensionsJson);
        if (!scoring.valid()) {
            return invalid(scoring.reasonCode(), scoring.message());
        }
        if (!hasConsumableContent(objectMapper, report.getStrengths())) {
            return invalid("STRENGTHS_MISSING", "Report strengths are missing");
        }
        if (!hasConsumableContent(objectMapper, report.getWeaknesses())) {
            return invalid("WEAKNESSES_MISSING", "Report weaknesses are missing");
        }
        if (!hasConsumableContent(objectMapper, report.getMainProblems())) {
            return invalid("MAIN_PROBLEMS_MISSING", "Report main problems are missing");
        }
        if (!hasConsumableContent(objectMapper, report.getReviewSuggestions())) {
            return invalid("REVIEW_SUGGESTIONS_MISSING", "Report review suggestions are missing");
        }
        if (!StringUtils.hasText(report.getReportContent())) {
            return invalid("REPORT_CONTENT_MISSING", "Report content is missing");
        }
        if (report.getGeneratedAt() == null) {
            return invalid("GENERATED_AT_MISSING", "Report generatedAt is missing");
        }
        return validateQaReview(objectMapper, report.getQaReview(), expectedAnswerCount);
    }

    private static Validation validateQaReview(ObjectMapper objectMapper,
                                               String qaReview,
                                               int expectedAnswerCount) {
        if (!StringUtils.hasText(qaReview)) {
            return invalid("QA_REVIEW_MISSING", "Report question reviews are missing");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(qaReview);
        } catch (Exception ex) {
            return invalid("QA_REVIEW_MALFORMED", "Report question reviews are malformed");
        }
        if (root == null || !root.isArray()) {
            return invalid("QA_REVIEW_MALFORMED", "Report question reviews are malformed");
        }
        if (root.size() != expectedAnswerCount) {
            return invalid("QA_REVIEW_COUNT_MISMATCH",
                    "Report question review count does not match answer evidence");
        }
        for (JsonNode item : root) {
            if (item == null || !item.isObject()
                    || !hasTextField(item, "question", "questionContent")
                    || !hasTextField(item, "answer", "userAnswer")
                    || (!hasNumericField(item, "score", "aiScore")
                    && !hasTextField(item, "comment", "aiComment"))) {
                return invalid("QA_REVIEW_ITEM_INVALID", "Report question review item is incomplete");
            }
        }
        return new Validation(true, null, null);
    }

    private static boolean hasConsumableContent(ObjectMapper objectMapper, String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || node.isNull()) {
                return false;
            }
            if (node.isArray()) {
                if (node.isEmpty()) {
                    return false;
                }
                for (JsonNode item : node) {
                    if (hasContent(item)) {
                        return true;
                    }
                }
                return false;
            }
            return hasContent(node);
        } catch (Exception ignored) {
            return StringUtils.hasText(value);
        }
    }

    private static boolean hasContent(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            return StringUtils.hasText(node.asText());
        }
        if (node.isNumber() || node.isBoolean()) {
            return true;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (hasContent(item)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isObject()) {
            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                if (hasContent(values.next())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasTextField(JsonNode item, String... fields) {
        for (String field : fields) {
            JsonNode value = item.get(field);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNumericField(JsonNode item, String... fields) {
        for (String field : fields) {
            JsonNode value = item.get(field);
            if (value != null && value.isNumber() && value.asDouble() >= 0D && value.asDouble() <= 100D) {
                return true;
            }
        }
        return false;
    }

    private static Validation invalid(String reasonCode, String message) {
        return new Validation(false, reasonCode, message);
    }

    public record Validation(boolean valid, String reasonCode, String message) {
    }
}
