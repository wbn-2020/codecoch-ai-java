package com.codecoachai.ai.domain.support;

import com.codecoachai.ai.domain.enums.AiResultSourceEnum;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Compatibility mapping for legacy AI provenance fields and the two-axis delivery contract.
 */
public final class AiDeliverySemantics {

    public static final String PRIMARY_MODEL = "PRIMARY_MODEL";
    public static final String FALLBACK_MODEL = "FALLBACK_MODEL";
    public static final String RULE_ENGINE = "RULE_ENGINE";
    public static final String MOCK = "MOCK";

    public static final String COMPLETE = "COMPLETE";
    public static final String DEGRADED = "DEGRADED";
    public static final String FAILED = "FAILED";

    private AiDeliverySemantics() {
    }

    public static Outcome resolve(String executionSource,
                                  String deliveryQuality,
                                  String legacyResultSource,
                                  String modelName,
                                  String routeTrace,
                                  String requestBody,
                                  String responseBody,
                                  Integer success) {
        boolean mockDetected = containsMock(executionSource)
                || containsMock(legacyResultSource)
                || containsMock(modelName)
                || containsMock(routeTrace)
                || containsMock(requestBody)
                || containsMock(responseBody);
        String source = mockDetected
                ? MOCK
                : firstText(
                        normalizeExecutionSource(executionSource),
                        sourceFromLegacy(legacyResultSource),
                        sourceFromRoute(routeTrace),
                        sourceFromEvidence(requestBody, responseBody),
                        hasText(modelName) || hasText(routeTrace) ? PRIMARY_MODEL : null);
        String quality = resolveQuality(deliveryQuality, legacyResultSource, source, success);
        return new Outcome(source, quality, legacyResultSource(source), FALLBACK_MODEL.equals(source));
    }

    public static Outcome fromLegacy(String legacyResultSource,
                                     String modelName,
                                     String routeTrace,
                                     String requestBody,
                                     String responseBody,
                                     Integer success) {
        return resolve(null, null, legacyResultSource, modelName, routeTrace, requestBody, responseBody, success);
    }

    /**
     * New business records cannot retain an unspecified source; legacy test fixtures and callers
     * without route metadata are recorded as the default primary path until their caller is upgraded.
     */
    public static Outcome forBusinessResult(String legacyResultSource,
                                            String modelName,
                                            String routeTrace,
                                            String requestBody,
                                            String responseBody,
                                            Integer success) {
        Outcome resolved = fromLegacy(legacyResultSource, modelName, routeTrace, requestBody, responseBody, success);
        if (resolved.executionSource() != null) {
            return resolved;
        }
        String quality = FAILED.equals(resolved.deliveryQuality()) ? FAILED : COMPLETE;
        return new Outcome(PRIMARY_MODEL, quality, AiResultSourceEnum.LLM, false);
    }

    private static String resolveQuality(String persistedQuality,
                                         String legacyResultSource,
                                         String source,
                                         Integer success) {
        if (Integer.valueOf(0).equals(success)) {
            return FAILED;
        }
        if (MOCK.equals(source) || RULE_ENGINE.equals(source)) {
            return DEGRADED;
        }
        String normalizedPersisted = normalizeQuality(persistedQuality);
        if (normalizedPersisted != null) {
            return normalizedPersisted;
        }
        AiResultSourceEnum legacy = AiResultSourceEnum.normalize(legacyResultSource);
        if (legacy == AiResultSourceEnum.DEGRADED) {
            return DEGRADED;
        }
        if (source != null) {
            return COMPLETE;
        }
        return null;
    }

    private static AiResultSourceEnum legacyResultSource(String source) {
        if (MOCK.equals(source)) {
            return AiResultSourceEnum.MOCK;
        }
        if (FALLBACK_MODEL.equals(source)) {
            return AiResultSourceEnum.FALLBACK;
        }
        if (RULE_ENGINE.equals(source)) {
            return AiResultSourceEnum.RULE;
        }
        if (PRIMARY_MODEL.equals(source)) {
            return AiResultSourceEnum.LLM;
        }
        return AiResultSourceEnum.UNKNOWN;
    }

    private static String sourceFromLegacy(String value) {
        AiResultSourceEnum source = AiResultSourceEnum.normalize(value);
        return switch (source) {
            case MOCK -> MOCK;
            case FALLBACK, DEGRADED -> FALLBACK_MODEL;
            case RULE -> RULE_ENGINE;
            case LLM -> PRIMARY_MODEL;
            case UNKNOWN -> null;
        };
    }

    private static String sourceFromRoute(String value) {
        String normalized = upper(value);
        if (!hasText(normalized)) {
            return null;
        }
        if (normalized.contains("MOCK")) {
            return MOCK;
        }
        if (normalized.contains("RULE") || normalized.contains("DETERMINISTIC")) {
            return RULE_ENGINE;
        }
        if (normalized.contains("->") || normalized.contains("FALLBACK") || normalized.contains("DEGRADED")) {
            return FALLBACK_MODEL;
        }
        return null;
    }

    private static String sourceFromEvidence(String requestBody, String responseBody) {
        String request = upper(requestBody);
        String response = upper(responseBody);
        if (request.contains("MOCK") || response.contains("MOCK")) {
            return MOCK;
        }
        if (request.contains("\"RESULTSOURCE\":\"RULE\"")
                || response.contains("\"RESULTSOURCE\":\"RULE\"")
                || request.contains("RULE_ENGINE")
                || response.contains("RULE_ENGINE")) {
            return RULE_ENGINE;
        }
        if (request.contains("FALLBACKUSED")
                || request.contains("DEGRADED")
                || response.contains("\"FALLBACK\":TRUE")
                || response.contains("\"TRUSTSTATUS\":\"FALLBACK\"")
                || response.contains("DEGRADED")) {
            return FALLBACK_MODEL;
        }
        return null;
    }

    private static String normalizeExecutionSource(String value) {
        String normalized = upper(value);
        if (!hasText(normalized)) {
            return null;
        }
        if (normalized.contains("MOCK")) {
            return MOCK;
        }
        if (normalized.contains("RULE") || normalized.contains("DETERMINISTIC")) {
            return RULE_ENGINE;
        }
        if (normalized.contains("FALLBACK") || normalized.contains("DEGRADED") || normalized.contains("->")) {
            return FALLBACK_MODEL;
        }
        if (PRIMARY_MODEL.equals(normalized) || "LLM".equals(normalized) || "REAL".equals(normalized)) {
            return PRIMARY_MODEL;
        }
        return null;
    }

    private static String normalizeQuality(String value) {
        String normalized = upper(value);
        if (COMPLETE.equals(normalized) || DEGRADED.equals(normalized) || FAILED.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private static boolean containsMock(String value) {
        return upper(value).contains("MOCK") || (value != null && value.contains("模拟"));
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    public record Outcome(String executionSource,
                          String deliveryQuality,
                          AiResultSourceEnum legacyResultSource,
                          boolean fallback) {
    }
}
