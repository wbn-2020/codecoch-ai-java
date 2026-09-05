package com.codecoachai.resume.feign.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignArchiveAiSourceVO {

    private String sourceSchemaVersion;
    private Long userId;
    private Long campaignId;
    private LocalDateTime dataCutoffAt;
    private String sourceStatus;
    private String sourceHash;
    private List<String> missingSections = new ArrayList<>();
    private Review review;
    private List<Pulse> pulses = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Review {
        private Long reviewId;
        private Long snapshotId;
        private Long campaignId;
        private Integer snapshotVersion;
        private String reviewStatus;
        private LocalDateTime dataCutoffAt;
        private String summary;
        private String confidenceLevel;
        private String resultSource;
        private Boolean fallback;
        private String fallbackReason;
        private JsonNode facts;
        private JsonNode coverage;
        private JsonNode limits;
        private JsonNode signals;
        private JsonNode memoryCandidates;
        private JsonNode experimentCandidates;
        private JsonNode nextCycleActions;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pulse {
        private Long pulseId;
        private Long snapshotId;
        private Long campaignId;
        private Integer snapshotVersion;
        private LocalDateTime dataCutoffAt;
        private String inputHash;
        private String confidenceLevel;
        private Boolean fallback;
        private JsonNode facts;
        private JsonNode metrics;
        private JsonNode changes;
        private JsonNode driftSignals;
        private JsonNode limits;
        private JsonNode actionSeeds;
        private JsonNode narrative;
    }
}
