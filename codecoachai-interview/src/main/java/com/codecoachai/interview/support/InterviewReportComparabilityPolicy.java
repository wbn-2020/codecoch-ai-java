package com.codecoachai.interview.support;

import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InterviewReportComparabilityPolicy {

    private final ObjectMapper objectMapper;

    public InterviewReportComparabilityPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result evaluate(InterviewReport report, InterviewSession session) {
        if (report == null || session == null) {
            return unavailable("REPORT_UNAVAILABLE", "Interview report is unavailable");
        }
        if (!"GENERATED".equalsIgnoreCase(report.getStatus())) {
            return unavailable("REPORT_NOT_GENERATED", "Only generated reports can be compared");
        }
        if (session.getTargetJobId() == null) {
            return unavailable("TARGET_JOB_MISSING", "Report target job is missing");
        }
        String rubricVersion = normalizeText(report.getRubricVersion());
        if (rubricVersion == null) {
            return unavailable("RUBRIC_VERSION_MISSING", "Report rubric version is missing");
        }
        if (report.getTotalScore() == null) {
            return unavailable("TOTAL_SCORE_MISSING", "Report total score is missing");
        }
        if (!StringUtils.hasText(report.getRubricScores())) {
            return unavailable("RUBRIC_DATA_MISSING", "Report has no comparable rubric dimensions");
        }

        Map<String, BigDecimal> dimensions;
        try {
            JsonNode root = objectMapper.readTree(report.getRubricScores());
            dimensions = normalizeDimensions(root);
        } catch (RubricDataException ex) {
            return unavailable(ex.reasonCode, ex.getMessage());
        } catch (Exception ex) {
            return unavailable("RUBRIC_DATA_MALFORMED", "Report rubric data is malformed");
        }
        if (dimensions.isEmpty()) {
            return unavailable("RUBRIC_DATA_MISSING", "Report has no valid numeric rubric dimensions");
        }
        return new Result(true, null, null, dimensions, session.getTargetJobId(),
                rubricVersion, report.getTotalScore());
    }

    public Result evaluateGroup(List<Result> reports) {
        if (reports == null || reports.isEmpty()) {
            return unavailable("REPORT_UNAVAILABLE", "Interview reports are unavailable");
        }
        if (reports.stream().anyMatch(Objects::isNull)) {
            return unavailable("REPORT_UNAVAILABLE", "Interview reports are unavailable");
        }
        Result unavailable = reports.stream()
                .filter(result -> !result.comparable())
                .findFirst()
                .orElse(null);
        if (unavailable != null) {
            return unavailable;
        }
        long targetJobCount = reports.stream().map(Result::targetJobId).distinct().count();
        if (targetJobCount > 1) {
            return unavailable("TARGET_JOB_MISMATCH", "Selected reports do not share the same target job");
        }
        long rubricVersionCount = reports.stream().map(Result::rubricVersion).distinct().count();
        if (rubricVersionCount > 1) {
            return unavailable("RUBRIC_VERSION_MISMATCH", "Selected reports use different rubric versions");
        }
        Result first = reports.get(0);
        if (reports.stream().skip(1).anyMatch(report ->
                !first.normalizedDimensions().keySet().equals(report.normalizedDimensions().keySet()))) {
            return unavailable("RUBRIC_DIMENSION_MISMATCH",
                    "Selected reports do not contain the same rubric dimensions");
        }
        return new Result(true, null, null, first.normalizedDimensions(),
                first.targetJobId(), first.rubricVersion(), first.totalScore());
    }

    private Map<String, BigDecimal> normalizeDimensions(JsonNode root) {
        if (root == null || !root.isArray()) {
            throw malformed();
        }
        if (root.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> dimensions = new LinkedHashMap<>();
        for (JsonNode item : root) {
            if (!item.isObject()) {
                throw malformed();
            }
            JsonNode dimensionNode = item.get("dimension");
            JsonNode score = item.get("score");
            if (dimensionNode == null || !dimensionNode.isTextual()
                    || score == null || !score.isNumber()) {
                throw malformed();
            }
            String dimension = normalizeDimension(dimensionNode.textValue());
            if (dimension == null) {
                throw malformed();
            }
            BigDecimal normalizedScore = score.decimalValue();
            if (normalizedScore.compareTo(BigDecimal.ONE) < 0
                    || normalizedScore.compareTo(BigDecimal.valueOf(5)) > 0) {
                throw malformed();
            }
            if (dimensions.putIfAbsent(dimension, normalizedScore) != null) {
                throw new RubricDataException(
                        "RUBRIC_DIMENSION_DUPLICATE",
                        "Report rubric data contains duplicate dimensions");
            }
        }
        return Collections.unmodifiableMap(dimensions);
    }

    private RubricDataException malformed() {
        return new RubricDataException("RUBRIC_DATA_MALFORMED", "Report rubric data is malformed");
    }

    private Result unavailable(String reasonCode, String message) {
        return new Result(false, reasonCode, message, Map.of(), null, null, null);
    }

    private String normalizeDimension(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record Result(
            boolean comparable,
            String reasonCode,
            String message,
            Map<String, BigDecimal> normalizedDimensions,
            Long targetJobId,
            String rubricVersion,
            Integer totalScore) {
    }

    private static final class RubricDataException extends RuntimeException {

        private final String reasonCode;

        private RubricDataException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }
    }
}
