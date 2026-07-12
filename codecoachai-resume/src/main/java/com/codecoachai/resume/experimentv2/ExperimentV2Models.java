package com.codecoachai.resume.experimentv2;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

public final class ExperimentV2Models {

    private ExperimentV2Models() {
    }

    @Data
    public static class HypothesisCreate {
        private Long legacyExperimentId;
        @NotBlank
        private String name;
        @NotBlank
        private String statement;
        private String primaryMetric = "INTERVIEW";
        @Min(1)
        @Max(90)
        private Integer attributionWindowDays = 14;
        @Min(2)
        @Max(100)
        private Integer minSamplePerVariant = 10;
        @Valid
        private List<VariantCreate> variants = new ArrayList<>();
    }

    @Data
    public static class HypothesisUpdate {
        private String name;
        private String statement;
        private String primaryMetric;
        @Min(1)
        @Max(90)
        private Integer attributionWindowDays;
        @Min(2)
        @Max(100)
        private Integer minSamplePerVariant;
        private String status;
    }

    @Data
    public static class VariantCreate {
        @NotBlank
        private String variantCode;
        @NotBlank
        private String name;
        private String description;
        private Map<String, Object> treatment;
        @Min(1)
        @Max(1000)
        private Integer allocationWeight = 1;
        private Boolean control = false;
    }

    @Data
    public static class AssignmentCreate {
        @NotNull
        private Long applicationId;
        private Long variantId;
        private String assignmentKey;
        private LocalDateTime assignedAt;
        private String jobFamily;
        private String channel;
    }

    @Data
    public static class CohortCreate {
        @NotBlank
        private String name;
        private String jobFamily;
        private String channel;
        @NotNull
        private LocalDateTime windowStart;
        @NotNull
        private LocalDateTime windowEnd;
        private String outcomeType;
        @Min(2)
        @Max(100)
        private Integer minSamplePerVariant;
    }

    @Data
    public static class HypothesisView {
        private Long id;
        private Long legacyExperimentId;
        private String name;
        private String statement;
        private String primaryMetric;
        private String status;
        private Integer attributionWindowDays;
        private Integer minSamplePerVariant;
        private List<VariantView> variants = new ArrayList<>();
        private List<CohortView> cohorts = new ArrayList<>();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class VariantView {
        private Long id;
        private String variantCode;
        private String name;
        private String description;
        private Map<String, Object> treatment;
        private Integer allocationWeight;
        private Boolean control;
    }

    @Data
    public static class AssignmentView {
        private Long id;
        private Long hypothesisId;
        private Long variantId;
        private String variantCode;
        private Long applicationId;
        private String assignmentKey;
        private String assignmentMethod;
        private LocalDateTime assignedAt;
        private String jobFamily;
        private String channel;
        private LocalDate timeBucket;
    }

    @Data
    public static class CohortView {
        private Long id;
        private Long hypothesisId;
        private String name;
        private String jobFamily;
        private String channel;
        private LocalDateTime windowStart;
        private LocalDateTime windowEnd;
        private String outcomeType;
        private Integer minSamplePerVariant;
    }

    @Data
    public static class AttributionView {
        private Long snapshotId;
        private Long hypothesisId;
        private Long cohortId;
        private LocalDateTime asOf;
        private String method;
        private Boolean comparable;
        private Integer eligibleSampleCount;
        private Integer immatureSampleCount;
        private Integer excludedMissingStrataCount;
        private Integer commonStrataCount;
        private List<String> incomparableReasons = new ArrayList<>();
        private List<String> limitations = new ArrayList<>();
        private List<VariantAttributionView> variants = new ArrayList<>();
    }

    @Data
    public static class VariantAttributionView {
        private Long variantId;
        private String variantCode;
        private Boolean control;
        private Integer assignedCount;
        private Integer matureCount;
        private Integer commonStrataSampleCount;
        private Integer outcomeCount;
        private BigDecimal rawRate;
        private BigDecimal adjustedRate;
        private BigDecimal adjustedLiftVsControl;
    }
}
