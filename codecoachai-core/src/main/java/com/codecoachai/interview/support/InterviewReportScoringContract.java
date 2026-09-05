package com.codecoachai.interview.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class InterviewReportScoringContract {

    private static final int MAX_DIMENSION_COUNT = 32;
    private static final BigDecimal MIN_RUBRIC_SCORE = BigDecimal.ONE;
    private static final BigDecimal MAX_RUBRIC_SCORE = BigDecimal.valueOf(5);
    private static final BigDecimal RUBRIC_TO_TOTAL_SCORE_SCALE = BigDecimal.valueOf(20);
    private static final int TOTAL_SCORE_TOLERANCE = 1;
    private static final Set<String> CURRENT_REQUIRED_DIMENSIONS = Set.of(
            "EXPRESSION_STRUCTURE",
            "TECHNICAL_DEPTH",
            "BUSINESS_UNDERSTANDING",
            "RISK_AWARENESS",
            "IMPLEMENTABILITY");

    private InterviewReportScoringContract() {
    }

    public static Validation validate(
            ObjectMapper objectMapper,
            Integer totalScore,
            String rubricVersion,
            String rubricScores) {
        return validate(objectMapper, totalScore, rubricVersion, rubricScores, null);
    }

    public static Validation validate(
            ObjectMapper objectMapper,
            Integer totalScore,
            String rubricVersion,
            String rubricScores,
            String expectedDimensionsJson) {
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
        Map<String, BigDecimal> scoresByDimension = new LinkedHashMap<>();
        BigDecimal dimensionScoreTotal = BigDecimal.ZERO;
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
            scoresByDimension.put(dimension, score);
            dimensionScoreTotal = dimensionScoreTotal.add(score);
            if (item.path("fallback").asBoolean(false)
                    || item.path("sampleInsufficient").asBoolean(false)) {
                return invalid(
                        "RUBRIC_DATA_UNTRUSTED",
                        "Fallback or sample-insufficient rubric data cannot be persisted as a formal score");
            }
        }
        Map<String, BigDecimal> expectedWeights = expectedDimensionWeights(
                objectMapper, rubricVersion, expectedDimensionsJson);
        if (expectedWeights != null) {
            if (!dimensions.equals(expectedWeights.keySet())) {
                return invalid(
                        "RUBRIC_DIMENSION_MISMATCH",
                        "Report rubric dimensions do not match the declared rubric version");
            }
            BigDecimal weightedScore = expectedWeights.entrySet().stream()
                    .map(entry -> scoresByDimension.get(entry.getKey()).multiply(entry.getValue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int derivedTotalScore = weightedScore
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                    .multiply(RUBRIC_TO_TOTAL_SCORE_SCALE)
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
            if (Math.abs(totalScore - derivedTotalScore) > TOTAL_SCORE_TOLERANCE) {
                return invalid(
                        "TOTAL_SCORE_MISMATCH",
                        "Report total score is inconsistent with rubric dimension scores");
            }
        }
        return new Validation(true, null, null);
    }

    private static Map<String, BigDecimal> expectedDimensionWeights(
            ObjectMapper objectMapper,
            String rubricVersion,
            String expectedDimensionsJson) {
        if (!StringUtils.hasText(expectedDimensionsJson)) {
            if (!InterviewRubricVersion.CURRENT.equalsIgnoreCase(rubricVersion.trim())) {
                return null;
            }
            BigDecimal equalWeight = BigDecimal.valueOf(100)
                    .divide(BigDecimal.valueOf(CURRENT_REQUIRED_DIMENSIONS.size()), 8, RoundingMode.HALF_UP);
            Map<String, BigDecimal> weights = new LinkedHashMap<>();
            CURRENT_REQUIRED_DIMENSIONS.forEach(dimension -> weights.put(dimension, equalWeight));
            return weights;
        }
        try {
            JsonNode expected = objectMapper.readTree(expectedDimensionsJson);
            if (expected == null || !expected.isArray() || expected.isEmpty()) {
                return Map.of();
            }
            Map<String, BigDecimal> weights = new LinkedHashMap<>();
            BigDecimal totalWeight = BigDecimal.ZERO;
            for (JsonNode dimension : expected) {
                JsonNode codeNode = firstNode(dimension, "code", "dimension", "dimensionCode");
                JsonNode weightNode = dimension.get("weight");
                if (codeNode == null || !codeNode.isTextual() || !StringUtils.hasText(codeNode.textValue())
                        || weightNode == null || !weightNode.isNumber()
                        || weightNode.decimalValue().compareTo(BigDecimal.ZERO) <= 0) {
                    return Map.of();
                }
                String code = codeNode.textValue().trim().toUpperCase(Locale.ROOT);
                BigDecimal weight = weightNode.decimalValue();
                if (weights.putIfAbsent(code, weight) != null) {
                    return Map.of();
                }
                totalWeight = totalWeight.add(weight);
            }
            return totalWeight.compareTo(BigDecimal.valueOf(100)) == 0 ? weights : Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
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
