package com.codecoachai.interview.support;

import com.codecoachai.interview.domain.entity.InterviewReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class InterviewReportTrustPolicy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> TRUSTED_EVIDENCE_SOURCES = Set.of(
            "PROJECT_EVIDENCE",
            "INTERVIEW_ANSWER",
            "INTERVIEW_REPORT",
            "JD_ANALYSIS",
            "RESUME_MATCH",
            "ABILITY_PROFILE",
            "AGENT_TASK");

    private InterviewReportTrustPolicy() {
    }

    public static boolean isTrustedForFormalAction(InterviewReport report) {
        if (report == null
                || !"GENERATED".equalsIgnoreCase(report.getStatus())
                || report.getId() == null
                || report.getSessionId() == null
                || report.getUserId() == null
                || report.getTotalScore() == null
                || report.getTotalScore() <= 0
                || report.getGeneratedAt() == null
                || !StringUtils.hasText(report.getSummary())
                || !StringUtils.hasText(report.getReportContent())
                || StringUtils.hasText(report.getFailureReason())) {
            return false;
        }
        if (containsTrueFlag(report.getQaReview(), "fallback")
                || containsTrueFlag(report.getRubricScores(), "fallback")
                || containsTrueFlag(report.getAdviceEvidence(), "fallback")
                || containsTrueFlag(report.getAbilityProfileUpdates(), "fallback")
                || containsTrueFlag(report.getRubricScores(), "sampleInsufficient")
                || containsTrueFlag(report.getAdviceEvidence(), "sampleInsufficient")
                || containsTrueFlag(report.getAbilityProfileUpdates(), "sampleInsufficient")) {
            return false;
        }
        return hasOnlyTrustedAdviceEvidence(report.getAdviceEvidence(), report.getId());
    }

    public static boolean isFallbackOrUntrusted(InterviewReport report) {
        if (report == null) {
            return true;
        }
        String status = report.getStatus();
        return "FAILED".equalsIgnoreCase(status)
                || "UNSCORABLE".equalsIgnoreCase(status)
                || StringUtils.hasText(report.getFailureReason())
                || containsTrueFlag(report.getQaReview(), "fallback")
                || containsTrueFlag(report.getAdviceEvidence(), "fallback");
    }

    public static boolean isSampleInsufficient(InterviewReport report) {
        return report == null
                || containsTrueFlag(report.getRubricScores(), "sampleInsufficient")
                || containsTrueFlag(report.getAdviceEvidence(), "sampleInsufficient")
                || containsTrueFlag(report.getAbilityProfileUpdates(), "sampleInsufficient");
    }

    public static boolean isTrustedAdvice(Map<String, Object> item, Long reportId) {
        if (item == null
                || Boolean.TRUE.equals(asBoolean(item.get("fallback")))
                || Boolean.TRUE.equals(asBoolean(item.get("sampleInsufficient")))) {
            return false;
        }
        Object sources = item.get("evidenceSources");
        if (!(sources instanceof Iterable<?> iterable)) {
            return false;
        }
        boolean found = false;
        for (Object source : iterable) {
            found = true;
            if (!isTrustedEvidenceSource(OBJECT_MAPPER.valueToTree(source), reportId)) {
                return false;
            }
        }
        return found;
    }

    private static boolean hasOnlyTrustedAdviceEvidence(String json, Long reportId) {
        if (!StringUtils.hasText(json)) {
            return true;
        }
        JsonNode root = readTree(json);
        if (root == null || !root.isArray()) {
            return false;
        }
        for (JsonNode item : root) {
            if (!item.isObject()) {
                return false;
            }
            JsonNode sources = item.get("evidenceSources");
            if (sources == null || !sources.isArray() || sources.isEmpty()) {
                return false;
            }
            for (JsonNode source : sources) {
                if (!isTrustedEvidenceSource(source, reportId)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isTrustedEvidenceSource(JsonNode source, Long reportId) {
        if (source == null || !source.isObject()) {
            return false;
        }
        String sourceType = source.path("sourceType").asText("").trim().toUpperCase(Locale.ROOT);
        if (!TRUSTED_EVIDENCE_SOURCES.contains(sourceType)) {
            return false;
        }
        long sourceId = source.path("sourceId").asLong(0L);
        if (sourceId <= 0L || !StringUtils.hasText(source.path("sourceSummary").asText(null))) {
            return false;
        }
        return !"INTERVIEW_REPORT".equals(sourceType)
                || reportId == null
                || reportId.longValue() == sourceId;
    }

    private static boolean containsTrueFlag(String json, String fieldName) {
        if (!StringUtils.hasText(json)) {
            return false;
        }
        JsonNode root = readTree(json);
        return root == null || containsTrueFlag(root, fieldName);
    }

    private static boolean containsTrueFlag(JsonNode node, String fieldName) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.asBoolean(false)) {
                return true;
            }
            for (JsonNode child : node) {
                if (containsTrueFlag(child, fieldName)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsTrueFlag(child, fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JsonNode readTree(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }
}
