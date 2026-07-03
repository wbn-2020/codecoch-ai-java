package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class JobSearchExperimentStrategyVO {

    private String title;
    private String content;
    private String confidenceLevel;
    private Boolean sampleInsufficient;
    private String sampleWarning;
    private String actionUrl;
    private List<EvidenceSource> evidenceSources = new ArrayList<>();

    @Data
    public static class EvidenceSource {
        private String sourceType;
        private Long sourceId;
        private String sourceSummary;
    }
}
