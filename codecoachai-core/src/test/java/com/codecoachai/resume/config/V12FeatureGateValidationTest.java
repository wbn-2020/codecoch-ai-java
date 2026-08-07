package com.codecoachai.resume.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class V12FeatureGateValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsUnsafeExperimentSampleThresholds() {
        V12FeatureGate gate = new V12FeatureGate();
        gate.getExperimentSampleThresholds().setMinApplications(4);
        gate.getExperimentSampleThresholds().setMinInterviews(0);

        Set<String> paths = validator.validate(gate).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertEquals(2, paths.size());
        assertTrue(paths.contains("experimentSampleThresholds.minApplications"));
        assertTrue(paths.contains("experimentSampleThresholds.minInterviews"));
    }

    @Test
    void acceptsConfiguredDefaults() {
        assertTrue(validator.validate(new V12FeatureGate()).isEmpty());
    }
}
