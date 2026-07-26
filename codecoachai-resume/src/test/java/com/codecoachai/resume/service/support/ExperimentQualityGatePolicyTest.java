package com.codecoachai.resume.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.config.V12FeatureGate;
import com.codecoachai.resume.service.support.ExperimentQualityGatePolicy.QualityDecision;
import com.codecoachai.resume.service.support.ExperimentQualityGatePolicy.SampleState;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExperimentQualityGatePolicyTest {

    @Test
    void defaultThresholdsKeepLegacyGateBehavior() {
        ExperimentQualityGatePolicy policy = new ExperimentQualityGatePolicy();

        assertEquals(SampleState.BLOCKED_FACT_ONLY,
                policy.evaluate(4, 5, Map.of()).state());
        assertEquals(SampleState.WARN_WEAK_OBSERVATION,
                policy.evaluate(14, 5, Map.of()).state());
        assertEquals(SampleState.WARN_INTERVIEW_BOUNDARY,
                policy.evaluate(15, 2, Map.of()).state());
        assertEquals(SampleState.PASS_REVIEWABLE,
                policy.evaluate(15, 3, Map.of("v1", 3, "v2", 3)).state());
    }

    @Test
    void configuredThresholdsShiftTheGateStates() {
        V12FeatureGate gate = new V12FeatureGate();
        gate.getExperimentSampleThresholds().setMinApplications(20);
        gate.getExperimentSampleThresholds().setMinInterviews(5);
        ExperimentQualityGatePolicy policy = new ExperimentQualityGatePolicy(gate);

        assertEquals(SampleState.WARN_WEAK_OBSERVATION,
                policy.evaluate(15, 5, Map.of()).state());
        assertEquals(SampleState.WARN_INTERVIEW_BOUNDARY,
                policy.evaluate(20, 4, Map.of()).state());
        assertEquals(SampleState.PASS_REVIEWABLE,
                policy.evaluate(20, 5, Map.of("v1", 3, "v2", 3)).state());
    }

    @Test
    void thresholdsAreReadAtEvaluationTimeForNacosRefresh() {
        V12FeatureGate gate = new V12FeatureGate();
        ExperimentQualityGatePolicy policy = new ExperimentQualityGatePolicy(gate);
        assertEquals(SampleState.PASS_REVIEWABLE, policy.evaluate(15, 3, Map.of()).state());

        gate.getExperimentSampleThresholds().setMinApplications(30);

        assertEquals(SampleState.WARN_WEAK_OBSERVATION,
                policy.evaluate(15, 3, Map.of()).state());
        assertEquals(30, policy.minReviewableApplications());
    }

    @Test
    void sampleBoundaryReportsConfiguredThresholds() {
        V12FeatureGate gate = new V12FeatureGate();
        gate.getExperimentSampleThresholds().setMinApplications(18);
        gate.getExperimentSampleThresholds().setMinInterviews(4);
        ExperimentQualityGatePolicy policy = new ExperimentQualityGatePolicy(gate);

        QualityDecision decision = policy.evaluate(10, 1, Map.of());

        assertEquals(18, decision.sampleBoundary().get("minReviewableApplications"));
        assertEquals(4, decision.sampleBoundary().get("minInterviewTrendSamples"));
        assertFalse(decision.strongConclusionAllowed());
        assertTrue(decision.unsupportedConclusions().stream()
                .anyMatch(text -> text.contains("面试能力")));
    }
}
