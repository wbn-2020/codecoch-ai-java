package com.codecoachai.ai.agent.campaignreview.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerCampaignReviewVO {

    private Long reviewId;
    private Long snapshotId;
    private Long campaignId;
    private Integer snapshotVersion;
    private String scene = "CAREER_CAMPAIGN_REVIEW_GENERATE";
    private String reportStatus;
    private String confidenceLevel;
    private Boolean fallback;
    private String fallbackReason;
    private String summary;
    private LocalDateTime dataCutoffAt;
    private List<Fact> facts = new ArrayList<>();
    private List<String> coverage = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    private List<Signal> signals = new ArrayList<>();
    private List<Seed> memoryCandidates = new ArrayList<>();
    private List<Seed> experimentCandidates = new ArrayList<>();
    private List<Seed> nextCycleActions = new ArrayList<>();

    @Data
    public static class Fact {
        private String key;
        private String label;
        private Object value;
        private String sourceRef;
    }

    @Data
    public static class Signal {
        private String key;
        private String title;
        private String description;
        private String confidenceLevel;
        private List<String> blockedConclusions = new ArrayList<>();
    }

    @Data
    public static class Seed {
        private Long candidateId;
        private String semanticKey;
        private String title;
        private String description;
        private String sourceRef;
        private String confidenceLevel;
        private Integer validityDays;
        private String status = "PENDING_CONFIRMATION";
        private Boolean effective = false;
    }
}
