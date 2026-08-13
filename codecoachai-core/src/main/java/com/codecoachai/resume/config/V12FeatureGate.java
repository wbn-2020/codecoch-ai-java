package com.codecoachai.resume.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@RefreshScope
@ConfigurationProperties(prefix = "codecoachai.features.v12")
public class V12FeatureGate {

    private boolean evidenceProfileFeedback;

    @Valid
    private ExperimentSampleThresholds experimentSampleThresholds = new ExperimentSampleThresholds();

    @Data
    public static class ExperimentSampleThresholds {

        @Min(5)
        private int minApplications = 15;

        @Min(1)
        private int minInterviews = 3;
    }
}
