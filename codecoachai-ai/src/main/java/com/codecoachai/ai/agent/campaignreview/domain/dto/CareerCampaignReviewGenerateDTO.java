package com.codecoachai.ai.agent.campaignreview.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerCampaignReviewGenerateDTO {

    @NotNull
    private Long campaignId;
    @NotBlank
    private String campaignStatus;
    @NotBlank
    private String idempotencyKey;
    private String requestId;
    @NotNull
    private Boolean completed;
    @NotNull
    private Boolean allOpportunitiesClosed;
    @NotNull
    private LocalDateTime dataCutoffAt;
    private Integer sampleSize = 0;
    private String campaignTitle;
    private List<Fact> facts = new ArrayList<>();
    private List<Seed> memoryCandidateSeeds = new ArrayList<>();
    private List<Seed> experimentCandidateSeeds = new ArrayList<>();
    private List<Seed> nextCycleActionSeeds = new ArrayList<>();
    private List<Source> sources = new ArrayList<>();

    @Data
    public static class Fact {
        private String key;
        private String label;
        private Object value;
        private String sourceRef;
    }

    @Data
    public static class Seed {
        private String semanticKey;
        private String title;
        private String description;
        private String sourceRef;
        private String confidenceLevel = "MEDIUM";
        private Integer validityDays;
        private Boolean causalClaim = false;
    }

    @Data
    public static class Source {
        private String sourceType;
        private Long sourceId;
        private Integer sourceVersion;
        private LocalDateTime sourceTime;
        private LocalDateTime sourceUpdatedAt;
        private String sourceHash;
    }
}
