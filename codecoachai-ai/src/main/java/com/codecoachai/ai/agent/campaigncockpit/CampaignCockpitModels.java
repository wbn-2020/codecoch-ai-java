package com.codecoachai.ai.agent.campaigncockpit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

public final class CampaignCockpitModels {

    private CampaignCockpitModels() {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EvidenceEnvelope {
        private Long userId;
        private Long campaignId;
        private String campaignStatus;
        private String campaignTitle;
        private LocalDateTime dataCutoffAt;
        private Campaign campaign;
        private OperatingProfile operatingProfile;
        private List<Application> applications = new ArrayList<>();
        private List<Fact> facts = new ArrayList<>();
        private List<EvidenceRef> sources = new ArrayList<>();
        private Map<String, Coverage> coverage = new LinkedHashMap<>();
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Campaign {
        private Long id;
        private String name;
        private String goal;
        private String title;
        private String status;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OperatingProfile {
        private Boolean configured = false;
        private Integer weeklyApplicationTarget = 5;
        private Integer weeklyTimeBudgetMinutes = 300;
        private Integer maxActiveOpportunities = 8;
        private Integer staleAfterDays = 14;
        private Integer defaultFollowUpDays = 5;
        private String timezone = "Asia/Shanghai";
        private List<String> focusRoles = new ArrayList<>();
        private List<String> focusLocations = new ArrayList<>();
        private List<String> focusChannels = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Application {
        private Long applicationId;
        private Long id;
        private String companyName;
        private String jobTitle;
        private String status;
        private String stage;
        private String priorityLevel;
        private LocalDateTime createdAt;
        private LocalDateTime nextFollowUpAt;
        private LocalDateTime stageUpdatedAt;
        private LocalDateTime updatedAt;
        private LocalDateTime interviewAt;
        private Boolean interviewPrepMissing;
        private Boolean interviewReviewMissing;
        private Boolean interviewPreparationReady;
        private Boolean interviewReviewReady;
        private LocalDateTime offerDeadlineAt;
        private Boolean materialCoverageLow;
        private Boolean researchCoverageLow;
        private Integer materialCoveragePercent;
        private Integer researchCoveragePercent;
        private LocalDateTime contactFollowUpAt;
        private String sourceHash;
        private String actionUrl;
        private Boolean active;
        private Boolean stale;
        @JsonAlias("sources")
        private List<EvidenceRef> evidenceRefs = new ArrayList<>();

        public Long stableId() {
            return applicationId == null ? id : applicationId;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fact {
        private String key;
        private String label;
        private Object value;
        private String sourceRef;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EvidenceRef {
        private String sourceType;
        private Long sourceId;
        private Integer sourceVersion;
        private String sourceHash;
        private Long applicationId;
        private Long campaignId;
        private LocalDateTime observedAt;
        private LocalDateTime sourceTime;
        private LocalDateTime sourceUpdatedAt;
        private String fieldPath;
        private String summary;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Coverage {
        private Boolean available = true;
        private Boolean truncated = false;
        private Integer itemCount = 0;
        private String warning;
    }

    @Data
    public static class CockpitView {
        private Campaign campaign;
        private OperatingProfile operatingProfile;
        private PulseSummary pulseSummary;
        private Map<String, Integer> stageDistribution = new LinkedHashMap<>();
        private DeadlineSummary deadlineSummary = new DeadlineSummary();
        private CoverageSummary coverageSummary = new CoverageSummary();
        private List<Application> applications = new ArrayList<>();
        private List<ActionItem> actionQueue = new ArrayList<>();
        private CapacitySummary capacitySummary = new CapacitySummary();
        private Map<String, Coverage> coverage = new LinkedHashMap<>();
        private List<String> warnings = new ArrayList<>();
        private LocalDateTime dataCutoffAt;
        private String confidenceLevel;
    }

    @Data
    public static class PulseSummary {
        private Long snapshotId;
        private Integer snapshotVersion;
        private String summary;
        private String confidenceLevel;
        private Boolean fallback;
        private LocalDateTime generatedAt;
    }

    @Data
    public static class DeadlineSummary {
        private Integer overdueCount = 0;
        private Integer dueTodayCount = 0;
        private Integer dueWithinSevenDaysCount = 0;
    }

    @Data
    public static class CoverageSummary {
        private Integer availableSections = 0;
        private Integer unavailableSections = 0;
        private Integer truncatedSections = 0;
    }

    @Data
    public static class CapacitySummary {
        private Integer availableMinutes = 0;
        private Integer usedMinutes = 0;
        private Integer weeklyBudgetMinutes = 0;
        private Integer openActionMinutes = 0;
        private Integer remainingMinutes = 0;
        private Integer activeOpportunityCount = 0;
        private Integer maxActiveOpportunities = 0;
        private Integer weeklyApplicationTarget = 0;
        private Integer weeklyApplications = 0;
        private Boolean overloaded = false;
    }

    @Data
    public static class ActionItem {
        private String semanticKey;
        private String sourceHash;
        private String actionType;
        private String title;
        private String description;
        private String priority;
        private List<String> priorityReasons = new ArrayList<>();
        private LocalDateTime dueAt;
        private Integer estimatedMinutes;
        private Long applicationId;
        private String applicationPriority;
        private String relatedBizType;
        private Long relatedBizId;
        private List<EvidenceRef> evidenceRefs = new ArrayList<>();
        private String actionUrl;
        private String confidenceLevel;
        private Boolean fallback = false;
        private String decisionStatus = "OPEN";
    }

    @Data
    public static class ActionDecisionRequest {
        @NotBlank
        @Size(max = 255)
        private String semanticKey;
        @NotBlank
        @Size(max = 80)
        private String sourceHash;
        @NotBlank
        @Size(max = 24)
        private String decisionStatus;
        private LocalDateTime snoozedUntil;
        @Size(max = 500)
        private String reason;
        @NotBlank
        @Size(min = 8, max = 128)
        private String idempotencyKey;
    }

    @Data
    public static class ActionDecisionView {
        private Long id;
        private Long campaignId;
        private String semanticKey;
        private String sourceHash;
        private String actionType;
        private String decisionStatus;
        private LocalDateTime snoozedUntil;
        private String reason;
        private LocalDateTime decidedAt;
    }

    @Data
    public static class ScenarioRequest {
        @NotNull
        @Min(0)
        @Max(10080)
        private Integer availableMinutes;
        @NotBlank
        private String focusMode;
        @Min(1)
        @Max(100)
        private Integer maxApplications = 10;
        private Boolean includeLowConfidence = false;
    }

    @Data
    public static class ScenarioPreview {
        private List<ActionItem> selectedActions = new ArrayList<>();
        private List<ActionItem> deferredActions = new ArrayList<>();
        private Integer totalEstimatedMinutes = 0;
        private Integer capacityRemainingMinutes = 0;
        private List<String> tradeoffs = new ArrayList<>();
        private List<String> limits = new ArrayList<>();
        private String sourceHash;
    }
}
