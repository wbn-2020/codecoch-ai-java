package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class InnerCareerEvidenceUsageFactsVO {

    private String schemaVersion = "V9_EVIDENCE_USAGE_INPUT_V1";
    private Long userId;
    private LocalDateTime dataCutoffAt;
    private String sourceSetHash;
    private List<UsageFact> usageSnapshots = new ArrayList<>();
    private List<ResultFact> confirmedResults = new ArrayList<>();
    private List<ExperimentAttributionFact> experimentAttributions = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    private Map<String, Object> coverage = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class UsageFact {
        private Long usageId;
        private Long applicationId;
        private Long campaignId;
        private Long targetJobId;
        private String assetType;
        private Long assetId;
        private String assetVersion;
        private Long packageSnapshotId;
        private String sourceHash;
        private String contentHash;
        private String usageScene;
        private LocalDateTime usedAt;
        private String status;
        private Boolean stale;
        private List<String> sourceRefs = new ArrayList<>();
    }

    @Data
    public static class ResultFact {
        private Long resultId;
        private Long usageId;
        private Long applicationId;
        private String eventType;
        private Long eventId;
        private String status;
        private Integer snapshotVersion;
        private String outcomeCode;
        private List<String> knownFacts = new ArrayList<>();
        private List<String> unknowns = new ArrayList<>();
        private List<String> limits = new ArrayList<>();
        private String sourceHash;
        private LocalDateTime occurredAt;
        private LocalDateTime confirmedAt;
    }

    @Data
    public static class ExperimentAttributionFact {
        private Long attributionId;
        private Long hypothesisId;
        private Long variantId;
        private Long assignmentId;
        private Long usageId;
        private String status;
        private String confidenceLevel;
        private Boolean fallback;
    }
}
