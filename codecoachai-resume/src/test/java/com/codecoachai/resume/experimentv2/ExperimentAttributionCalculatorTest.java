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
                List.of(
                        point(1L, true, true, "BACKEND", "REFERRAL", "2026-06-01"),
                        point(1L, true, false, "BACKEND", "JOB_BOARD", "2026-06-08"),
                        point(2L, true, true, "BACKEND", "REFERRAL", "2026-06-01"),
                        point(2L, true, true, "BACKEND", "JOB_BOARD", "2026-06-08"))));

        assertTrue(result.getComparable());
        assertEquals(2, result.getCommonStrataCount());
        assertEquals(new BigDecimal("0.5000"), result.getVariants().get(0).getAdjustedRate());
        assertEquals(new BigDecimal("1.0000"), result.getVariants().get(1).getAdjustedRate());
        assertEquals(new BigDecimal("0.5000"), result.getVariants().get(1).getAdjustedLiftVsControl());
        assertTrue(result.getLimitations().contains("CORRECTED_ASSOCIATION_NOT_CAUSAL_PROOF"));
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
}
