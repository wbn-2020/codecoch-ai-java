package com.codecoachai.interview.support;

import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InterviewReportComparabilityPolicy {

    public static final String FALLBACK_RUBRIC_VERSION = "INTERVIEW_FALLBACK_RUBRIC_V1";
    public static final String LEGACY_STAGE_RUBRIC_PREFIX = "LEGACY_STAGE_SCORES_V1:";
    public static final String LEGACY_RUBRIC_PREFIX = "LEGACY_RUBRIC_SCORES_V1:";

    private static final int MAX_DIMENSION_COUNT = 32;
    private static final BigDecimal MIN_RUBRIC_SCORE = BigDecimal.ONE;
    private static final BigDecimal MAX_RUBRIC_SCORE = BigDecimal.valueOf(5);
    private static final BigDecimal MIN_STAGE_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_STAGE_SCORE = BigDecimal.valueOf(100);
    private static final Pattern SCENARIO_RUBRIC_VERSION =
            Pattern.compile("^scenario:\\d+:rubric:(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> FALLBACK_RUBRIC_DIMENSIONS = Set.of(
            "EXPRESSION_STRUCTURE",
            "TECHNICAL_DEPTH",
            "BUSINESS_UNDERSTANDING",
            "RISK_AWARENESS",
            "IMPLEMENTABILITY");

    private final ObjectMapper objectMapper;

    public InterviewReportComparabilityPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result evaluate(InterviewReport report, InterviewSession session) {
        if (report == null || session == null) {
            return unavailable("REPORT_UNAVAILABLE", "Interview report is unavailable",
                    Map.of(), null, null, null, null, List.of());
        }
        if (!"GENERATED".equalsIgnoreCase(report.getStatus())) {
            return unavailable("REPORT_NOT_GENERATED", "Only generated reports can be compared",
                    Map.of(), session.getTargetJobId(), null, null, null, List.of());
        }

        List<Notice> warnings = new ArrayList<>();
        Integer totalScore = null;
        NormalizationException totalFailure = null;
        try {
            totalScore = resolveTotalScore(report, session, warnings);
        } catch (NormalizationException ex) {
            totalFailure = ex;
        }

        RubricResolution rubric = null;
        NormalizationException rubricFailure = null;
        try {
            rubric = resolveRubric(report, totalScore, warnings);
        } catch (NormalizationException ex) {
            rubricFailure = ex;
        }

        Map<String, BigDecimal> dimensions = rubric == null ? Map.of() : rubric.dimensions();
        String rubricVersion = rubric == null ? null : rubric.rubricVersion();
        String normalizationSource = rubric == null ? null : rubric.source();
        if (session.getTargetJobId() == null) {
            return unavailable("TARGET_JOB_MISSING", "Report target job is missing",
                    dimensions, null, rubricVersion, totalScore, normalizationSource, warnings);
        }
        if (totalFailure != null) {
            return unavailable(totalFailure.reasonCode, totalFailure.getMessage(),
                    dimensions, session.getTargetJobId(), rubricVersion, null, normalizationSource, warnings);
        }
        if (rubricFailure != null) {
            return unavailable(rubricFailure.reasonCode, rubricFailure.getMessage(),
                    Map.of(), session.getTargetJobId(), null, totalScore, null, warnings);
        }
        if (!InterviewReportTrustPolicy.isTrustedForComparisonNormalization(report, totalScore)) {
            return unavailable("REPORT_UNTRUSTED",
                    "Report scoring data is fallback, incomplete, or untrusted",
                    Map.of(), session.getTargetJobId(), rubricVersion, totalScore,
                    normalizationSource, warnings);
        }
        return new Result(true, null, null, dimensions, session.getTargetJobId(),
                rubricVersion, totalScore, normalizationSource, immutableNotices(warnings));
    }

    public Result evaluateGroup(List<Result> reports) {
        if (reports == null || reports.isEmpty()) {
            return unavailable("REPORT_UNAVAILABLE", "Interview reports are unavailable",
                    Map.of(), null, null, null, "GROUP", List.of());
        }
        if (reports.stream().anyMatch(Objects::isNull)) {
            return unavailable("REPORT_UNAVAILABLE", "Interview reports are unavailable",
                    Map.of(), null, null, null, "GROUP", List.of());
        }

        List<Notice> warnings = reports.stream()
                .flatMap(report -> report.normalizationWarnings().stream())
                .distinct()
                .toList();
        Result unavailable = reports.stream()
                .filter(result -> !result.comparable())
                .findFirst()
                .orElse(null);
        if (unavailable != null) {
            return unavailable(unavailable.reasonCode(), unavailable.message(), Map.of(),
                    commonTargetJob(reports), commonRubricVersion(reports), null, "GROUP", warnings);
        }

        long targetJobCount = reports.stream().map(Result::targetJobId).distinct().count();
        if (targetJobCount > 1) {
            return unavailable("TARGET_JOB_MISMATCH", "Selected reports do not share the same target job",
                    Map.of(), null, commonRubricVersion(reports), null, "GROUP", warnings);
        }
        long rubricVersionCount = reports.stream().map(Result::rubricVersion).distinct().count();
        if (rubricVersionCount > 1) {
            return unavailable("RUBRIC_VERSION_MISMATCH", "Selected reports use different rubric versions",
                    Map.of(), commonTargetJob(reports), null, null, "GROUP", warnings);
        }
        Result first = reports.get(0);
        if (reports.stream().skip(1).anyMatch(report ->
                !first.normalizedDimensions().keySet().equals(report.normalizedDimensions().keySet()))) {
            return unavailable("RUBRIC_DIMENSION_MISMATCH",
                    "Selected reports do not contain the same rubric dimensions",
                    Map.of(), first.targetJobId(), first.rubricVersion(), null, "GROUP", warnings);
        }
        return new Result(true, null, null, first.normalizedDimensions(),
                first.targetJobId(), first.rubricVersion(), first.totalScore(),
                "GROUP", immutableNotices(warnings));
    }

    private Integer resolveTotalScore(
            InterviewReport report, InterviewSession session, List<Notice> warnings) {
        if (report.getTotalScore() != null) {
            return validateTotalScore(report.getTotalScore());
        }
        if (session.getTotalScore() != null && session.getTotalScore() > 0) {
            Integer totalScore = validateTotalScore(session.getTotalScore());
            warnings.add(new Notice(
                    "TOTAL_SCORE_RECOVERED_FROM_SESSION",
                    "Report total score was missing; reused the persisted total score from the same interview session."));
            return totalScore;
        }
        throw new NormalizationException("TOTAL_SCORE_MISSING", "Report total score is missing");
    }

    private Integer validateTotalScore(Integer score) {
        if (score == null || score < 1 || score > 100) {
            throw new NormalizationException(
                    "TOTAL_SCORE_INVALID", "Report total score is outside the supported 1-100 range");
        }
        return score;
    }

    private RubricResolution resolveRubric(
            InterviewReport report, Integer normalizedTotalScore, List<Notice> warnings) {
        if (StringUtils.hasText(report.getRubricScores())) {
            Map<String, BigDecimal> dimensions = parseRubricDimensions(report.getRubricScores());
            if (dimensions.isEmpty()) {
                throw new NormalizationException(
                        "RUBRIC_DATA_MISSING", "Report has no valid numeric rubric dimensions");
            }
            String version = normalizeRubricVersion(report, dimensions, normalizedTotalScore, warnings);
            return new RubricResolution(dimensions, version, "REPORT_RUBRIC_SCORES");
        }

        if (StringUtils.hasText(report.getStageScores())) {
            Map<String, BigDecimal> dimensions = parseStageDimensions(report.getStageScores());
            if (!dimensions.isEmpty()) {
                requireTrustedLegacyReport(report, normalizedTotalScore);
                warnings.add(new Notice(
                        "RUBRIC_RECOVERED_FROM_STAGE_SCORES",
                        "Rubric dimensions were recovered from exact numeric legacy stage scores without estimating values."));
                warnings.add(new Notice(
                        "RUBRIC_VERSION_INFERRED",
                        "A compatibility rubric identity was derived from the exact legacy stage dimension contract."));
                return new RubricResolution(dimensions,
                        LEGACY_STAGE_RUBRIC_PREFIX + dimensionFingerprint(dimensions.keySet()),
                        "LEGACY_STAGE_SCORES");
            }
        }

        RubricResolution embedded = resolveEmbeddedRubric(report, normalizedTotalScore, warnings);
        if (embedded != null) {
            return embedded;
        }
        throw new NormalizationException(
                "RUBRIC_DATA_MISSING", "Report has no comparable rubric dimensions");
    }

    private RubricResolution resolveEmbeddedRubric(
            InterviewReport report, Integer normalizedTotalScore, List<Notice> warnings) {
        if (!StringUtils.hasText(report.getReportContent())) {
            return null;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(report.getReportContent());
        } catch (Exception ignored) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }

        JsonNode rubricNode = firstNode(root, "rubricScores", "rubric", "dimensionScores");
        if (rubricNode != null && !rubricNode.isNull()) {
            Map<String, BigDecimal> dimensions = normalizeRubricDimensions(readEmbeddedNode(rubricNode));
            if (!dimensions.isEmpty()) {
                String version = normalizeRubricVersion(report, dimensions, normalizedTotalScore, warnings);
                warnings.add(new Notice(
                        "RUBRIC_RECOVERED_FROM_REPORT_CONTENT",
                        "Rubric dimensions were recovered from the structured historical report payload."));
                return new RubricResolution(dimensions, version, "REPORT_CONTENT_RUBRIC");
            }
        }

        JsonNode stageNode = firstNode(root, "stageScores", "stageReports");
        if (stageNode != null && !stageNode.isNull()) {
            Map<String, BigDecimal> dimensions = normalizeStageDimensions(readEmbeddedNode(stageNode));
            if (!dimensions.isEmpty()) {
                requireTrustedLegacyReport(report, normalizedTotalScore);
                warnings.add(new Notice(
                        "RUBRIC_RECOVERED_FROM_REPORT_CONTENT",
                        "Legacy stage dimensions were recovered from the structured historical report payload."));
                warnings.add(new Notice(
                        "RUBRIC_VERSION_INFERRED",
                        "A compatibility rubric identity was derived from the exact legacy stage dimension contract."));
                return new RubricResolution(dimensions,
                        LEGACY_STAGE_RUBRIC_PREFIX + dimensionFingerprint(dimensions.keySet()),
                        "REPORT_CONTENT_STAGE_SCORES");
            }
        }
        return null;
    }

    private JsonNode readEmbeddedNode(JsonNode node) {
        if (!node.isTextual()) {
            return node;
        }
        try {
            return objectMapper.readTree(node.textValue());
        } catch (Exception ex) {
            throw malformed();
        }
    }

    private String normalizeRubricVersion(
            InterviewReport report,
            Map<String, BigDecimal> dimensions,
            Integer normalizedTotalScore,
            List<Notice> warnings) {
        String rawVersion = normalizeText(report.getRubricVersion());
        if (InterviewReportTrustPolicy.isFallbackOrUntrusted(report)
                && FALLBACK_RUBRIC_DIMENSIONS.equals(dimensions.keySet())) {
            if (!FALLBACK_RUBRIC_VERSION.equals(rawVersion)) {
                warnings.add(new Notice(
                        "RUBRIC_VERSION_NORMALIZED",
                        "The known fallback dimension contract was normalized to its stable compatibility rubric."));
            }
            return FALLBACK_RUBRIC_VERSION;
        }
        if (rawVersion != null) {
            Matcher matcher = SCENARIO_RUBRIC_VERSION.matcher(rawVersion);
            if (matcher.matches()) {
                warnings.add(new Notice(
                        "RUBRIC_VERSION_NORMALIZED",
                        "Scenario-specific report metadata was normalized to the immutable rubric version identity."));
                return "RUBRIC_ID:" + matcher.group(1);
            }
            return rawVersion;
        }

        requireTrustedLegacyReport(report, normalizedTotalScore);
        warnings.add(new Notice(
                "RUBRIC_VERSION_INFERRED",
                "A compatibility rubric identity was derived from the exact historical dimension contract."));
        return LEGACY_RUBRIC_PREFIX + dimensionFingerprint(dimensions.keySet());
    }

    private void requireTrustedLegacyReport(InterviewReport report, Integer normalizedTotalScore) {
        if (!InterviewReportTrustPolicy.isTrustedForComparisonNormalization(report, normalizedTotalScore)) {
            throw new NormalizationException(
                    "LEGACY_REPORT_UNTRUSTED",
                    "Legacy rubric data cannot be inferred because the report is fallback, incomplete, or untrusted");
        }
    }

    private Map<String, BigDecimal> parseRubricDimensions(String json) {
        try {
            return normalizeRubricDimensions(objectMapper.readTree(json));
        } catch (NormalizationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw malformed();
        }
    }

    private Map<String, BigDecimal> parseStageDimensions(String json) {
        try {
            return normalizeStageDimensions(objectMapper.readTree(json));
        } catch (NormalizationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw malformed();
        }
    }

    private Map<String, BigDecimal> normalizeRubricDimensions(JsonNode root) {
        if (root == null || !root.isArray()) {
            throw malformed();
        }
        Map<String, BigDecimal> dimensions = new LinkedHashMap<>();
        for (JsonNode item : root) {
            if (!item.isObject()) {
                throw malformed();
            }
            JsonNode dimensionNode = firstNode(item, "dimension", "dimensionCode", "code");
            JsonNode scoreNode = firstNode(item, "score", "dimensionScore", "value");
            if (dimensionNode == null || !dimensionNode.isTextual()
                    || scoreNode == null || !scoreNode.isNumber()) {
                throw malformed();
            }
            putDimension(dimensions, dimensionNode.textValue(), scoreNode.decimalValue(),
                    MIN_RUBRIC_SCORE, MAX_RUBRIC_SCORE);
        }
        return immutableDimensions(dimensions);
    }

    private Map<String, BigDecimal> normalizeStageDimensions(JsonNode root) {
        if (root == null || (!root.isObject() && !root.isArray())) {
            throw malformed();
        }
        Map<String, BigDecimal> dimensions = new LinkedHashMap<>();
        if (root.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (value.isNumber()) {
                    putDimension(dimensions, field.getKey(), value.decimalValue(),
                            MIN_STAGE_SCORE, MAX_STAGE_SCORE);
                    continue;
                }
                if (!value.isObject()) {
                    throw malformed();
                }
                JsonNode scoreNode = firstNode(value, "score", "stageScore", "value");
                JsonNode dimensionNode = firstNode(value, "dimension", "stageCode", "code", "stageName", "name");
                if (scoreNode == null || !scoreNode.isNumber()) {
                    throw malformed();
                }
                String dimension = dimensionNode != null && dimensionNode.isTextual()
                        ? dimensionNode.textValue() : field.getKey();
                putDimension(dimensions, dimension, scoreNode.decimalValue(),
                        MIN_STAGE_SCORE, MAX_STAGE_SCORE);
            }
        } else {
            for (JsonNode item : root) {
                if (!item.isObject()) {
                    throw malformed();
                }
                JsonNode dimensionNode = firstNode(
                        item, "dimension", "stageCode", "code", "stageName", "name");
                JsonNode scoreNode = firstNode(item, "score", "stageScore", "value");
                if (dimensionNode == null || !dimensionNode.isTextual()
                        || scoreNode == null || !scoreNode.isNumber()) {
                    throw malformed();
                }
                putDimension(dimensions, dimensionNode.textValue(), scoreNode.decimalValue(),
                        MIN_STAGE_SCORE, MAX_STAGE_SCORE);
            }
        }
        return immutableDimensions(dimensions);
    }

    private void putDimension(
            Map<String, BigDecimal> dimensions,
            String rawDimension,
            BigDecimal score,
            BigDecimal minimum,
            BigDecimal maximum) {
        if (dimensions.size() >= MAX_DIMENSION_COUNT) {
            throw new NormalizationException(
                    "RUBRIC_DIMENSION_LIMIT_EXCEEDED", "Report contains too many rubric dimensions");
        }
        String dimension = normalizeDimension(rawDimension);
        if (dimension == null || dimension.length() > 128
                || score == null
                || score.compareTo(minimum) < 0
                || score.compareTo(maximum) > 0) {
            throw malformed();
        }
        if (dimensions.putIfAbsent(dimension, score) != null) {
            throw new NormalizationException(
                    "RUBRIC_DIMENSION_DUPLICATE",
                    "Report rubric data contains duplicate dimensions");
        }
    }

    private JsonNode firstNode(JsonNode parent, String... fields) {
        if (parent == null || !parent.isObject() || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = parent.get(field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String dimensionFingerprint(Collection<String> dimensions) {
        try {
            String canonical = dimensions.stream()
                    .sorted(Comparator.naturalOrder())
                    .reduce((left, right) -> left + "|" + right)
                    .orElse("");
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                value.append(String.format("%02x", digest[index]));
            }
            return value.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to fingerprint interview rubric dimensions", ex);
        }
    }

    private Long commonTargetJob(List<Result> reports) {
        List<Long> values = reports.stream()
                .map(Result::targetJobId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return values.size() == 1 ? values.get(0) : null;
    }

    private String commonRubricVersion(List<Result> reports) {
        List<String> values = reports.stream()
                .map(Result::rubricVersion)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return values.size() == 1 ? values.get(0) : null;
    }

    private NormalizationException malformed() {
        return new NormalizationException(
                "RUBRIC_DATA_MALFORMED", "Report rubric data is malformed");
    }

    private Result unavailable(
            String reasonCode,
            String message,
            Map<String, BigDecimal> dimensions,
            Long targetJobId,
            String rubricVersion,
            Integer totalScore,
            String normalizationSource,
            List<Notice> warnings) {
        return new Result(false, reasonCode, message,
                dimensions == null ? Map.of() : dimensions,
                targetJobId, rubricVersion, totalScore, normalizationSource,
                immutableNotices(warnings));
    }

    private Map<String, BigDecimal> immutableDimensions(Map<String, BigDecimal> dimensions) {
        return dimensions.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
    }

    private List<Notice> immutableNotices(List<Notice> notices) {
        return notices == null || notices.isEmpty() ? List.of() : List.copyOf(notices);
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
            Integer totalScore,
            String normalizationSource,
            List<Notice> normalizationWarnings) {
    }

    public record Notice(String code, String message) {
    }

    private record RubricResolution(
            Map<String, BigDecimal> dimensions,
            String rubricVersion,
            String source) {
    }

    private static final class NormalizationException extends RuntimeException {

        private final String reasonCode;

        private NormalizationException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }
    }
}
