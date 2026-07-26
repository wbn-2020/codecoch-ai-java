package com.codecoachai.resume.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "codecoachai.features.v12")
public class V12FeatureGate {

    private boolean evidenceProfileFeedback;

    private ExperimentSampleThresholds experimentSampleThresholds = new ExperimentSampleThresholds();

    @Data
    public static class ExperimentSampleThresholds {

        private int minApplications = 15;

        private int minInterviews = 3;
    }
}
