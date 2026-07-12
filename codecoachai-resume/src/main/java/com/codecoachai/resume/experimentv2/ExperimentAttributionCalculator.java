package com.codecoachai.resume.experimentv2;

import com.codecoachai.resume.experimentv2.ExperimentV2Models.AttributionView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.VariantAttributionView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ExperimentAttributionCalculator {

    public AttributionView calculate(CalculationInput input) {
        AttributionView result = new AttributionView();
        result.setMethod("STANDARDIZED_STRATIFIED_RATE");
        result.setAsOf(input.asOf());
        result.setHypothesisId(input.hypothesisId());
        result.setCohortId(input.cohortId());

        List<VariantSpec> variants = input.variants() == null ? List.of() : input.variants();
        List<DataPoint> points = input.points() == null ? List.of() : input.points();
        Map<Long, VariantSpec> variantsById = variants.stream()
                .collect(Collectors.toMap(VariantSpec::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, List<DataPoint>> pointsByVariant = points.stream()
                .collect(Collectors.groupingBy(DataPoint::variantId));
        List<DataPoint> mature = points.stream().filter(DataPoint::mature).toList();
        List<DataPoint> complete = mature.stream().filter(this::hasCompleteStrata).toList();

        result.setEligibleSampleCount(mature.size());
        result.setImmatureSampleCount(points.size() - mature.size());
        result.setExcludedMissingStrataCount(mature.size() - complete.size());
        result.getLimitations().add("CORRECTED_ASSOCIATION_NOT_CAUSAL_PROOF");
        result.getLimitations().add("ONLY_OBSERVED_APPLICATION_EVENTS_ARE_ATTRIBUTED");
        if (result.getImmatureSampleCount() > 0) {
            result.getLimitations().add("IMMATURE_ASSIGNMENTS_EXCLUDED");
        }
        if (result.getExcludedMissingStrataCount() > 0) {
            result.getLimitations().add("MISSING_STRATIFICATION_ROWS_EXCLUDED");
        }

        if (variants.size() < 2) {
            result.getIncomparableReasons().add("LESS_THAN_TWO_VARIANTS");
        }
        long controlCount = variants.stream().filter(VariantSpec::control).count();
        if (controlCount != 1) {
            result.getIncomparableReasons().add("EXACTLY_ONE_CONTROL_VARIANT_REQUIRED");
        }
        if (mature.isEmpty() && !points.isEmpty()) {
            result.getIncomparableReasons().add("ATTRIBUTION_WINDOW_NOT_MATURE");
        }
        for (VariantSpec variant : variants) {
            int matureCount = (int) pointsByVariant.getOrDefault(variant.id(), List.of()).stream()
                    .filter(DataPoint::mature)
                    .count();
            if (matureCount < input.minSamplePerVariant()) {
                result.getIncomparableReasons().add(
                        "LOW_SAMPLE_VARIANT:" + variant.code() + ":" + matureCount + "<" + input.minSamplePerVariant());
            }
        }

        Set<String> commonStrata = commonStrata(variants, complete);
        result.setCommonStrataCount(commonStrata.size());
        if (commonStrata.isEmpty()) {
            result.getIncomparableReasons().add(complete.isEmpty()
                    ? "NO_COMPLETE_STRATIFICATION_ROWS"
                    : "NO_COMMON_JOB_FAMILY_CHANNEL_TIME_STRATA");
        }

        Map<String, Long> pooledStrataCounts = complete.stream()
                .filter(point -> commonStrata.contains(stratum(point)))
                .collect(Collectors.groupingBy(this::stratum, Collectors.counting()));
        long pooledTotal = pooledStrataCounts.values().stream().mapToLong(Long::longValue).sum();
        Map<Long, BigDecimal> adjustedRates = new HashMap<>();

        for (VariantSpec variant : variants) {
            List<DataPoint> assigned = pointsByVariant.getOrDefault(variant.id(), List.of());
            List<DataPoint> variantMature = assigned.stream().filter(DataPoint::mature).toList();
            List<DataPoint> variantCommon = variantMature.stream()
                    .filter(this::hasCompleteStrata)
                    .filter(point -> commonStrata.contains(stratum(point)))
                    .toList();
            int outcomes = (int) variantMature.stream().filter(DataPoint::outcome).count();

            VariantAttributionView view = new VariantAttributionView();
            view.setVariantId(variant.id());
            view.setVariantCode(variant.code());
            view.setControl(variant.control());
            view.setAssignedCount(assigned.size());
            view.setMatureCount(variantMature.size());
            view.setCommonStrataSampleCount(variantCommon.size());
            view.setOutcomeCount(outcomes);
            view.setRawRate(rate(outcomes, variantMature.size()));
            BigDecimal adjustedRate = adjustedRate(variantCommon, pooledStrataCounts, pooledTotal);
            view.setAdjustedRate(adjustedRate);
            adjustedRates.put(variant.id(), adjustedRate);
            result.getVariants().add(view);
        }

        BigDecimal controlRate = variants.stream()
                .filter(VariantSpec::control)
                .findFirst()
                .map(variant -> adjustedRates.get(variant.id()))
                .orElse(null);
        if (controlRate != null) {
            for (VariantAttributionView view : result.getVariants()) {
                if (view.getAdjustedRate() != null) {
                    view.setAdjustedLiftVsControl(view.getAdjustedRate().subtract(controlRate)
                            .setScale(4, RoundingMode.HALF_UP));
                }
            }
        }
        result.setComparable(result.getIncomparableReasons().isEmpty());
        return result;
    }

    private Set<String> commonStrata(List<VariantSpec> variants, List<DataPoint> complete) {
        Set<String> common = null;
        for (VariantSpec variant : variants) {
            Set<String> variantStrata = complete.stream()
                    .filter(point -> variant.id().equals(point.variantId()))
                    .map(this::stratum)
                    .collect(Collectors.toCollection(HashSet::new));
            if (common == null) {
                common = variantStrata;
            } else {
                common.retainAll(variantStrata);
            }
        }
        return common == null ? Set.of() : common;
    }

    private BigDecimal adjustedRate(List<DataPoint> points, Map<String, Long> pooledCounts, long pooledTotal) {
        if (points.isEmpty() || pooledTotal == 0) {
            return null;
        }
        Map<String, List<DataPoint>> byStratum = points.stream().collect(Collectors.groupingBy(this::stratum));
        BigDecimal adjusted = BigDecimal.ZERO;
        for (Map.Entry<String, Long> pooled : pooledCounts.entrySet()) {
            List<DataPoint> stratumPoints = byStratum.getOrDefault(pooled.getKey(), List.of());
            if (stratumPoints.isEmpty()) {
                return null;
            }
            long outcomes = stratumPoints.stream().filter(DataPoint::outcome).count();
            BigDecimal stratumRate = BigDecimal.valueOf(outcomes)
                    .divide(BigDecimal.valueOf(stratumPoints.size()), 8, RoundingMode.HALF_UP);
            BigDecimal weight = BigDecimal.valueOf(pooled.getValue())
                    .divide(BigDecimal.valueOf(pooledTotal), 8, RoundingMode.HALF_UP);
            adjusted = adjusted.add(stratumRate.multiply(weight));
        }
        return adjusted.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(int numerator, int denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private boolean hasCompleteStrata(DataPoint point) {
        return StringUtils.hasText(point.jobFamily())
                && !"UNKNOWN".equalsIgnoreCase(point.jobFamily())
                && StringUtils.hasText(point.channel())
                && !"UNKNOWN".equalsIgnoreCase(point.channel())
                && point.timeBucket() != null;
    }

    private String stratum(DataPoint point) {
        return point.jobFamily().trim().toUpperCase()
                + "|" + point.channel().trim().toUpperCase()
                + "|" + point.timeBucket();
    }

    public record VariantSpec(Long id, String code, boolean control) {
    }

    public record DataPoint(Long variantId, boolean mature, boolean outcome,
                            String jobFamily, String channel, java.time.LocalDate timeBucket) {
    }

    public record CalculationInput(Long hypothesisId, Long cohortId, java.time.LocalDateTime asOf,
                                   int minSamplePerVariant, List<VariantSpec> variants, List<DataPoint> points) {
    }
}
