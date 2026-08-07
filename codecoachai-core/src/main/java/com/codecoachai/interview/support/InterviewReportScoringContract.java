package com.codecoachai.interview.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class InterviewReportScoringContract {

    private static final int MAX_DIMENSION_COUNT = 32;
    private static final BigDecimal MIN_RUBRIC_SCORE = BigDecimal.ONE;
    private static final BigDecimal MAX_RUBRIC_SCORE = BigDecimal.valueOf(5);

    private InterviewReportScoringContract() {
    }

    public static Validation validate(
            ObjectMapper objectMapper,
            Integer totalScore,
            String rubricVersion,
            String rubricScores) {
        if (totalScore == null) {
            return invalid("TOTAL_SCORE_MISSING", "Report total score is missing");
        }
        if (totalScore < 1 || totalScore > 100) {
            return invalid(
                    "TOTAL_SCORE_INVALID",
                    "Report total score is outside the supported 1-100 range");
        }
        if (!StringUtils.hasText(rubricVersion)) {
            return invalid("RUBRIC_VERSION_MISSING", "Report rubric version is missing");
        }
        if (!StringUtils.hasText(rubricScores)) {
            return invalid("RUBRIC_DATA_MISSING", "Report has no rubric dimensions");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rubricScores);
        } catch (Exception ex) {
            return invalid("RUBRIC_DATA_MALFORMED", "Report rubric data is malformed");
        }
        if (root == null || !root.isArray()) {
            return invalid("RUBRIC_DATA_MALFORMED", "Report rubric data is malformed");
        }
        if (root.isEmpty()) {
            return invalid("RUBRIC_DATA_MISSING", "Report has no rubric dimensions");
        }
        if (root.size() > MAX_DIMENSION_COUNT) {
            return invalid(
                    "RUBRIC_DIMENSION_LIMIT_EXCEEDED",
                    "Report contains too many rubric dimensions");
        }

        Set<String> dimensions = new HashSet<>();
        for (JsonNode item : root) {
            if (!item.isObject()) {
                return invalid("RUBRIC_DATA_MALFORMED", "Report rubric data is malformed");
            }
            JsonNode dimensionNode = firstNode(item, "dimension", "dimensionCode", "code");
            JsonNode scoreNode = firstNode(item, "score", "dimensionScore", "value");
            if (dimensionNode == null
                    || !dimensionNode.isTextual()
                    || !StringUtils.hasText(dimensionNode.textValue())
                    || scoreNode == null
                    || !scoreNode.isNumber()) {
                return invalid("RUBRIC_DATA_MALFORMED", "Report rubric data is malformed");
            }
            BigDecimal score = scoreNode.decimalValue();
            if (score.compareTo(MIN_RUBRIC_SCORE) < 0
                    || score.compareTo(MAX_RUBRIC_SCORE) > 0) {
                return invalid("RUBRIC_DATA_MALFORMED", "Report rubric data is malformed");
            }
            String dimension = dimensionNode.textValue().trim().toUpperCase(Locale.ROOT);
            if (dimension.length() > 128) {
                return invalid("RUBRIC_DATA_MALFORMED", "Report rubric data is malformed");
            }
            if (!dimensions.add(dimension)) {
                return invalid(
                        "RUBRIC_DIMENSION_DUPLICATE",
                        "Report rubric data contains duplicate dimensions");
            }
            if (item.path("fallback").asBoolean(false)
                    || item.path("sampleInsufficient").asBoolean(false)) {
                return invalid(
                        "RUBRIC_DATA_UNTRUSTED",
                        "Fallback or sample-insufficient rubric data cannot be persisted as a formal score");
            }
        }
        return new Validation(true, null, null);
    }

    private static JsonNode firstNode(JsonNode parent, String... fields) {
        for (String field : fields) {
            JsonNode value = parent.get(field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Validation invalid(String reasonCode, String message) {
        return new Validation(false, reasonCode, message);
    }

    public record Validation(boolean valid, String reasonCode, String message) {
    }
}
