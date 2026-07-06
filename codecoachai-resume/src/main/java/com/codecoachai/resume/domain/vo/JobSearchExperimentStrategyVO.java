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
    private String resultSource;
    private Boolean fallback = false;
    private Map<String, Object> sampleBoundary;
    private List<String> unsupportedConclusions = new ArrayList<>();
    private List<String> weakObservations = new ArrayList<>();
    private List<Map<String, Object>> nextActions = new ArrayList<>();
    private List<Map<String, Object>> actionCandidates = new ArrayList<>();
    private List<EvidenceSource> evidenceSources = new ArrayList<>();
    private Map<String, Object> qualityGate;
    private Map<String, Object> reviewDsl;

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
