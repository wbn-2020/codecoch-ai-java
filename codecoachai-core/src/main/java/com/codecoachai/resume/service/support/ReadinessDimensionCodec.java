package com.codecoachai.resume.service.support;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ReadinessDimensionCodec {

    public static final String SCHEMA_VERSION = "readiness-dimensions-v1";
    public static final String LEGACY_SCHEMA_VERSION = "readiness-dimensions-v0";

    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS =
            Set.of(SCHEMA_VERSION, LEGACY_SCHEMA_VERSION);
    private static final Set<String> REQUIRED_DIMENSIONS =
            Set.of("RESUME", "PROJECT_EVIDENCE", "KNOWLEDGE", "INTERVIEW", "APPLICATION");
    private static final Set<String> CONFIDENCE_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    private final ObjectMapper objectMapper;

    public String encode(List<JobReadinessSnapshotVO.DimensionScore> dimensions) {
        ValidationIssue issue = validate(dimensions, false);
        if (issue != null) {
            throw new BusinessException(ErrorCode.SEMANTIC_VALIDATION_ERROR,
                    "readiness dimensions are invalid: " + issue.message());
        }
        try {
            String json = objectMapper.writeValueAsString(dimensions);
            DecodeResult roundTrip = decode(json, SCHEMA_VERSION);
            if (!roundTrip.valid()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "readiness dimensions failed round-trip validation");
            }
            return json;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "readiness dimensions could not be serialized");
        }
    }

    public DecodeResult decode(String raw, String schemaVersion) {
        String effectiveVersion = StringUtils.hasText(schemaVersion)
                ? schemaVersion.trim()
                : LEGACY_SCHEMA_VERSION;
        if (!SUPPORTED_SCHEMA_VERSIONS.contains(effectiveVersion)) {
            return DecodeResult.invalid(ValidationStatus.UNSUPPORTED_SCHEMA,
                    effectiveVersion, "unsupported schema version");
        }
        if (!StringUtils.hasText(raw)) {
            return DecodeResult.invalid(ValidationStatus.EMPTY,
                    effectiveVersion, "dimension JSON is empty");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (LEGACY_SCHEMA_VERSION.equals(effectiveVersion) && root != null && root.isTextual()) {
                root = objectMapper.readTree(root.asText());
            }
            if (root == null || !root.isArray()) {
                return DecodeResult.invalid(ValidationStatus.INVALID_STRUCTURE,
                        effectiveVersion, "dimension JSON root must be an array");
            }
            List<JobReadinessSnapshotVO.DimensionScore> dimensions =
                    objectMapper.readerForListOf(JobReadinessSnapshotVO.DimensionScore.class)
                            .readValue(root.toString());
            boolean legacy = LEGACY_SCHEMA_VERSION.equals(effectiveVersion);
            if (legacy) {
                normalizeLegacyDefaults(dimensions);
            }
            ValidationIssue issue = validate(dimensions, legacy);
            if (issue != null) {
                return DecodeResult.invalid(ValidationStatus.INVALID_STRUCTURE,
                        effectiveVersion, issue.message());
            }
            if (legacy) {
                dimensions.forEach(item -> {
                    if (item.getSampleInsufficient() == null) {
                        item.setSampleInsufficient(item.getSampleCount() == null
                                || item.getSampleCount() == 0);
                    }
                });
            }
            return new DecodeResult(
                    legacy ? ValidationStatus.VALID_LEGACY : ValidationStatus.VALID,
                    effectiveVersion,
                    List.copyOf(dimensions),
                    null);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return DecodeResult.invalid(ValidationStatus.INVALID_JSON,
                    effectiveVersion, "dimension JSON is malformed");
        } catch (Exception ex) {
            return DecodeResult.invalid(ValidationStatus.INVALID_STRUCTURE,
                    effectiveVersion, "dimension JSON does not match the schema");
        }
    }

    private void normalizeLegacyDefaults(List<JobReadinessSnapshotVO.DimensionScore> dimensions) {
        if (dimensions == null) {
            return;
        }
        dimensions.forEach(item -> {
            if (item == null) {
                return;
            }
            int sampleCount = item.getSampleCount() == null ? 0 : item.getSampleCount();
            if (!StringUtils.hasText(item.getConfidenceLevel())) {
                item.setConfidenceLevel(sampleCount <= 0 ? "LOW" : sampleCount == 1 ? "MEDIUM" : "HIGH");
            }
            if (item.getFallback() == null) {
                item.setFallback(false);
            }
            if (item.getSampleInsufficient() == null) {
                item.setSampleInsufficient(sampleCount == 0);
            }
        });
    }

    private ValidationIssue validate(List<JobReadinessSnapshotVO.DimensionScore> dimensions,
                                     boolean legacy) {
        if (dimensions == null || dimensions.isEmpty()) {
            return new ValidationIssue("at least one dimension is required");
        }
        Set<String> names = new HashSet<>();
        for (JobReadinessSnapshotVO.DimensionScore item : dimensions) {
            if (item == null || !StringUtils.hasText(item.getDimension())) {
                return new ValidationIssue("dimension name is required");
            }
            String name = item.getDimension().trim().toUpperCase(Locale.ROOT);
            item.setDimension(name);
            if (!REQUIRED_DIMENSIONS.contains(name)) {
                return new ValidationIssue("unknown dimension: " + name);
            }
            if (!names.add(name)) {
                return new ValidationIssue("duplicate dimension: " + name);
            }
            if (item.getScore() == null || item.getScore() < 0 || item.getScore() > 100) {
                return new ValidationIssue("dimension score must be between 0 and 100");
            }
            if (item.getSampleCount() == null || item.getSampleCount() < 0) {
                return new ValidationIssue("dimension sampleCount must be zero or greater");
            }
            if (!StringUtils.hasText(item.getConfidenceLevel())) {
                return new ValidationIssue("dimension confidenceLevel is required");
            }
            String confidence = item.getConfidenceLevel().trim().toUpperCase(Locale.ROOT);
            item.setConfidenceLevel(confidence);
            if (!CONFIDENCE_LEVELS.contains(confidence)) {
                return new ValidationIssue("unsupported confidenceLevel: " + confidence);
            }
            if (item.getFallback() == null) {
                return new ValidationIssue("dimension fallback is required");
            }
            if (!legacy && item.getSampleInsufficient() == null) {
                return new ValidationIssue("dimension sampleInsufficient is required");
            }
        }
        if (!names.equals(REQUIRED_DIMENSIONS)) {
            return new ValidationIssue("all five readiness dimensions are required");
        }
        return null;
    }

    public enum ValidationStatus {
        VALID,
        VALID_LEGACY,
        EMPTY,
        INVALID_JSON,
        INVALID_STRUCTURE,
        UNSUPPORTED_SCHEMA
    }

    public record DecodeResult(
            ValidationStatus status,
            String schemaVersion,
            List<JobReadinessSnapshotVO.DimensionScore> dimensions,
            String reason) {

        public boolean valid() {
            return status == ValidationStatus.VALID || status == ValidationStatus.VALID_LEGACY;
        }

        static DecodeResult invalid(ValidationStatus status, String schemaVersion, String reason) {
            return new DecodeResult(status, schemaVersion, List.of(), reason);
        }
    }

    private record ValidationIssue(String message) {
    }
}
