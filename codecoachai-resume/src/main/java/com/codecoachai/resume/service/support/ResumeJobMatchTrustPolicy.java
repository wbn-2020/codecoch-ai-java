package com.codecoachai.resume.service.support;

import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ResumeJobMatchTrustPolicy {

    public static final String TRUST_VERIFIED = "VERIFIED";
    public static final String TRUST_PARTIAL = "PARTIAL";
    public static final String TRUST_FALLBACK = "FALLBACK";

    private final ObjectMapper objectMapper;

    public Assessment assess(ResumeJobMatchReport report) {
        RawResult rawResult = readRawResult(report);
        JsonNode raw = rawResult.value();
        String rawTrustValue = raw.path("trustStatus").asText(null);
        String rawTrustStatus = normalizeTrustStatus(rawTrustValue);
        ArrayNode schemaWarnings = schemaWarnings(raw);
        appendStoredFieldWarnings(report, schemaWarnings);
        int schemaWarningCount = schemaWarnings.size();

        boolean generationFailed = report != null
                && ResumeJobMatchStatus.FAILED.getCode().equals(report.getStatus());
        boolean fallback = generationFailed
                || raw.path("fallback").asBoolean(false)
                || TRUST_FALLBACK.equals(rawTrustStatus);
        boolean invalidTrustStatus = StringUtils.hasText(rawTrustValue) && rawTrustStatus == null;
        boolean malformedWarnings = raw.has("schemaWarnings")
                && !raw.path("schemaWarnings").isNull()
                && !raw.path("schemaWarnings").isArray();
        boolean requiresReview = fallback
                || !hasTrustedSuccessEvidence(report)
                || rawResult.invalid()
                || invalidTrustStatus
                || malformedWarnings
                || TRUST_PARTIAL.equals(rawTrustStatus)
                || schemaWarningCount > 0;
        String trustStatus = fallback
                ? TRUST_FALLBACK
                : requiresReview ? TRUST_PARTIAL : TRUST_VERIFIED;
        boolean trustedSuccess = report != null
                && ResumeJobMatchStatus.SUCCESS.getCode().equals(report.getStatus())
                && TRUST_VERIFIED.equals(trustStatus);

        return new Assessment(
                trustStatus,
                fallback,
                requiresReview,
                trustedSuccess,
                schemaWarningCount,
                schemaWarnings);
    }

    private boolean hasTrustedSuccessEvidence(ResumeJobMatchReport report) {
        if (report == null
                || !ResumeJobMatchStatus.SUCCESS.getCode().equals(report.getStatus())
                || report.getResumeId() == null
                || report.getTargetJobId() == null
                || report.getOverallScore() == null
                || report.getOverallScore() < 0
                || report.getOverallScore() > 100
                || report.getAiCallLogId() == null) {
            return false;
        }
        return StringUtils.hasText(report.getStrengthsJson())
                || StringUtils.hasText(report.getGapsJson())
                || StringUtils.hasText(report.getSummary());
    }

    private RawResult readRawResult(ResumeJobMatchReport report) {
        String rawResultJson = report == null ? null : report.getRawResultJson();
        if (!StringUtils.hasText(rawResultJson)) {
            return new RawResult(objectMapper.getNodeFactory().missingNode(), false);
        }
        try {
            JsonNode parsed = objectMapper.readTree(rawResultJson);
            if (parsed == null || !parsed.isObject()) {
                return new RawResult(objectMapper.getNodeFactory().missingNode(), true);
            }
            return new RawResult(parsed, false);
        } catch (Exception ignored) {
            return new RawResult(objectMapper.getNodeFactory().missingNode(), true);
        }
    }

    private ArrayNode schemaWarnings(JsonNode raw) {
        ArrayNode warnings = objectMapper.createArrayNode();
        JsonNode source = raw.path("schemaWarnings");
        if (source.isArray()) {
            source.forEach(warnings::add);
        }
        return warnings;
    }

    private void appendStoredFieldWarnings(ResumeJobMatchReport report, ArrayNode warnings) {
        if (report == null) {
            return;
        }
        validateStoredArray(report.getStrengthsJson(), "strengths", warnings);
        validateStoredArray(report.getGapsJson(), "gaps", warnings);
        validateStoredArray(report.getResumeRisksJson(), "resumeRisks", warnings);
        validateStoredArray(report.getOptimizationSuggestionsJson(), "optimizationSuggestions", warnings);
        validateStoredArray(report.getRecommendedLearningTopicsJson(), "recommendedLearningTopics", warnings);
        validateStoredArray(report.getRecommendedInterviewTopicsJson(), "recommendedInterviewTopics", warnings);
    }

    private void validateStoredArray(String raw, String fieldName, ArrayNode warnings) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            if (parsed == null || !parsed.isArray()) {
                addSchemaWarning(
                        warnings, fieldName, "stored field was not an array and was wrapped for display");
            }
        } catch (Exception ignored) {
            addSchemaWarning(
                    warnings, fieldName, "stored field JSON was malformed and was hidden for display");
        }
    }

    private void addSchemaWarning(ArrayNode warnings, String fieldName, String message) {
        warnings.add(objectMapper.createObjectNode()
                .put("field", fieldName)
                .put("message", message));
    }

    private String normalizeTrustStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case TRUST_VERIFIED, TRUST_PARTIAL, TRUST_FALLBACK -> normalized;
            default -> null;
        };
    }

    public record Assessment(
            String trustStatus,
            boolean fallback,
            boolean requiresReview,
            boolean trustedSuccess,
            int schemaWarningCount,
            JsonNode schemaWarnings) {
    }

    private record RawResult(JsonNode value, boolean invalid) {
    }
}
