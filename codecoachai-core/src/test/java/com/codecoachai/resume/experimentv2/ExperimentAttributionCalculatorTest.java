package com.codecoachai.resume.experimentv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.experimentv2.ExperimentAttributionCalculator.CalculationInput;
import com.codecoachai.resume.experimentv2.ExperimentAttributionCalculator.DataPoint;
import com.codecoachai.resume.experimentv2.ExperimentAttributionCalculator.VariantSpec;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AttributionView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExperimentAttributionCalculatorTest {

    private final ExperimentAttributionCalculator calculator = new ExperimentAttributionCalculator();
    private final List<VariantSpec> variants = List.of(
            new VariantSpec(1L, "CONTROL", true),
            new VariantSpec(2L, "TREATMENT", false));

    @Test
    void blocksComparisonWhenEitherVariantIsBelowMinimumSample() {
        AttributionView result = calculator.calculate(new CalculationInput(
                10L, 20L, LocalDateTime.of(2026, 7, 1, 0, 0), 3, variants,
                List.of(
                        point(1L, true, false, "BACKEND", "REFERRAL", "2026-06-01"),
                        point(1L, true, true, "BACKEND", "REFERRAL", "2026-06-01"),
                        point(2L, true, true, "BACKEND", "REFERRAL", "2026-06-01"),
                        point(2L, true, true, "BACKEND", "REFERRAL", "2026-06-01"))));

        assertFalse(result.getComparable());
        assertTrue(result.getIncomparableReasons().stream()
                .anyMatch(reason -> reason.startsWith("LOW_SAMPLE_VARIANT:CONTROL")));
        assertTrue(result.getIncomparableReasons().stream()
                .anyMatch(reason -> reason.startsWith("LOW_SAMPLE_VARIANT:TREATMENT")));
    }

    @Test
    void standardizesRatesAcrossCommonJobFamilyChannelAndTimeStrata() {
        AttributionView result = calculator.calculate(new CalculationInput(
                10L, 20L, LocalDateTime.of(2026, 7, 1, 0, 0), 2, variants,
                standardizedPoints()));

        assertTrue(result.getComparable());
        assertEquals("PASS", result.getQualityGate());
        assertEquals("HIGH", result.getConfidenceLevel());
        assertEquals("REVIEWABLE_WITH_BOUNDARY", result.getSampleLevel());
        assertEquals(16, result.getEligibleSampleCount());
        assertEquals(2, result.getCommonStrataCount());
        assertEquals(new BigDecimal("0.5000"), result.getVariants().get(0).getAdjustedRate());
        assertEquals(new BigDecimal("1.0000"), result.getVariants().get(1).getAdjustedRate());
        assertEquals(new BigDecimal("0.5000"), result.getVariants().get(1).getAdjustedLiftVsControl());
        assertTrue(result.getLimitations().contains("CORRECTED_ASSOCIATION_NOT_CAUSAL_PROOF"));
    }

    @Test
    void withFourteenMatureRowsItKeepsRatesAsWeakObservationOnly() {
        List<DataPoint> points = new ArrayList<>();
        List<DataPoint> base = List.of(
                point(1L, true, true, "BACKEND", "REFERRAL", "2026-06-01"),
                point(1L, true, false, "BACKEND", "JOB_BOARD", "2026-06-08"),
                point(2L, true, true, "BACKEND", "REFERRAL", "2026-06-01"),
                point(2L, true, true, "BACKEND", "JOB_BOARD", "2026-06-08"));
        for (int i = 0; i < 3; i++) {
            points.addAll(base);
        }
        points.add(point(1L, true, true, "BACKEND", "REFERRAL", "2026-06-01"));
        points.add(point(2L, true, true, "BACKEND", "JOB_BOARD", "2026-06-08"));

        AttributionView result = calculator.calculate(new CalculationInput(
                10L, 20L, LocalDateTime.of(2026, 7, 1, 0, 0), 2, variants,
                points, 3, java.util.Map.of("v1", 7, "v2", 7)));

        assertFalse(result.getComparable());
        assertEquals("WARN", result.getQualityGate());
        assertEquals("LOW", result.getConfidenceLevel());
        assertEquals("WEAK_OBSERVATION", result.getSampleLevel());
        assertTrue(result.getVariants().stream()
                .allMatch(variant -> variant.getAdjustedRate() == null));
        assertTrue(result.getUnsupportedConclusions().stream()
                .anyMatch(value -> value.contains("强策略结论")));
    }

    @Test
    void explainsNoCommonStrataInsteadOfComparingUnlikeCohorts() {
        AttributionView result = calculator.calculate(new CalculationInput(
                10L, 20L, LocalDateTime.of(2026, 7, 1, 0, 0), 1, variants,
                List.of(
                        point(1L, true, true, "BACKEND", "REFERRAL", "2026-06-01"),
                        point(2L, true, true, "FRONTEND", "JOB_BOARD", "2026-06-08"))));

        assertFalse(result.getComparable());
        assertEquals(0, result.getCommonStrataCount());
        assertTrue(result.getIncomparableReasons().contains("NO_COMMON_JOB_FAMILY_CHANNEL_TIME_STRATA"));
    }

    private DataPoint point(Long variantId, boolean mature, boolean outcome,
                            String family, String channel, String bucket) {
        return new DataPoint(variantId, mature, outcome, family, channel, LocalDate.parse(bucket));
    }

    private List<DataPoint> standardizedPoints() {
        List<DataPoint> base = List.of(
                point(1L, true, true, "BACKEND", "REFERRAL", "2026-06-01"),
                point(1L, true, false, "BACKEND", "JOB_BOARD", "2026-06-08"),
                point(2L, true, true, "BACKEND", "REFERRAL", "2026-06-01"),
                point(2L, true, true, "BACKEND", "JOB_BOARD", "2026-06-08"));
        List<DataPoint> points = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            points.addAll(base);
        }
        return points;
    }
}
