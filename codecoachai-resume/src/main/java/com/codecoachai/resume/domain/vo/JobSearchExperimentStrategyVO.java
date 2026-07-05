package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class JobSearchExperimentStrategyVO {

    private String title;
    private String content;
    private String confidenceLevel;
    private Boolean sampleInsufficient;
    private String sampleWarning;
    private String actionUrl;
    private List<String> unsupportedConclusions = new ArrayList<>();
    private List<String> weakObservations = new ArrayList<>();
    private List<EvidenceSource> evidenceSources = new ArrayList<>();

    @Data
    public static class EvidenceSource {
        private String sourceType;
        private Long sourceId;
        private String sourceSummary;
        private String trustStatus;
        private LocalDateTime sourceUpdatedAt;
        private Map<String, Object> metadata;
    }
}
