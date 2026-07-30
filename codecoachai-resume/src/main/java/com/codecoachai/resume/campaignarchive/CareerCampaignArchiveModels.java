package com.codecoachai.resume.campaignarchive;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

public final class CareerCampaignArchiveModels {

    private CareerCampaignArchiveModels() {
    }

    @Data
    public static class CreateRequest {
        private LocalDateTime dataCutoffAt;
        private String exportFormat = "ZIP";
        @NotBlank
        @Size(min = 8, max = 128)
        private String idempotencyKey;
        private Boolean retryFailed = false;
    }

    @Data
    public static class View {
        private Long id;
        private Long userId;
        private Long campaignId;
        private LocalDateTime dataCutoffAt;
        private String exportFormat;
        private String status;
        private String sourceHash;
        private String manifestHash;
        private Long fileId;
        private Long fileSize;
        private String errorCode;
        private String errorMessage;
        private String idempotencyKeyHash;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CampaignRow {
        private Long id;
        private Long userId;
        private String name;
        private String goal;
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime archivedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApplicationRow {
        private Long id;
        private Long targetJobId;
        private Long resumeVersionId;
        private Long matchReportId;
        private String companyName;
        private String jobTitle;
        private String source;
        private String status;
        private LocalDateTime stageChangedAt;
        private String priorityLevel;
        private String opportunityOutcome;
        private LocalDateTime appliedAt;
        private LocalDateTime nextFollowUpAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimelineRow {
        private String sourceType;
        private Long sourceId;
        private Long applicationId;
        private String eventType;
        private LocalDateTime eventAt;
        private String summary;
        @JsonIgnore
        private Long sortId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CalendarRow {
        private Long id;
        private Long applicationId;
        private String title;
        private String eventType;
        private LocalDateTime startsAtUtc;
        private LocalDateTime endsAtUtc;
        private String timezone;
        private Integer allDayFlag;
        private String location;
        private String status;
        private String sourceType;
        private String sourceRef;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InterviewRow {
        private Long processId;
        private Long roundId;
        private Long applicationId;
        private Integer roundNo;
        private String roundType;
        private String title;
        private String timezone;
        private LocalDateTime scheduledStartsAtUtc;
        private LocalDateTime scheduledEndsAtUtc;
        private String status;
        private String resultSummary;
        private String nextStep;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OfferRow {
        private Long offerId;
        private Long applicationId;
        private String status;
        private LocalDateTime decisionDeadline;
        private LocalDateTime finalizedAt;
        private Integer versionNo;
        private String currency;
        private BigDecimal annualBaseSalary;
        private BigDecimal annualBonus;
        private BigDecimal signOnBonus;
        private BigDecimal annualEquityValue;
        private BigDecimal otherAnnualCompensation;
        private Integer paidLeaveDays;
        private String location;
        private String workMode;
        private LocalDate startDate;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContactRow {
        private Long contactId;
        private Long applicationId;
        private String displayName;
        private String roleType;
        private String channelType;
        private String maskedContactHint;
        private String relationshipSummary;
        private String relationshipType;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActivityRow {
        private Long id;
        private Long applicationId;
        private Long contactId;
        private String activityType;
        private String channelType;
        private String subject;
        private String summary;
        private LocalDateTime occurredAt;
        private LocalDateTime nextFollowUpAt;
        private String status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResearchSnapshotRow {
        private Long id;
        private Long reportId;
        private Long applicationId;
        private String sourceSetHash;
        private String confidenceLevel;
        private String fallback;
        @JsonIgnore
        private String snapshotJson;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ArchiveBundle {
        private CampaignRow campaign;
        private List<ApplicationRow> applications = new ArrayList<>();
        private List<TimelineRow> timeline = new ArrayList<>();
        private List<CalendarRow> calendar = new ArrayList<>();
        private List<InterviewRow> interviews = new ArrayList<>();
        private List<OfferRow> offers = new ArrayList<>();
        private List<ContactRow> contacts = new ArrayList<>();
        private List<ActivityRow> activities = new ArrayList<>();
        private List<ResearchSnapshotRow> researchSnapshots = new ArrayList<>();
        private List<EvidenceUsageRow> evidenceUsages = new ArrayList<>();
        private List<EvidenceUsageResultRow> evidenceUsageResults = new ArrayList<>();
        private SectionMetadata evidenceUsageSection = new SectionMetadata();
        private SectionMetadata evidenceUsageResultsSection = new SectionMetadata();
        private JsonNode agentPulses;
        private String campaignReviewMarkdown;
        private String aiSourceHash;
        private List<String> missingSections = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    public static class SectionMetadata {
        private boolean available = true;
        private List<String> missingSections = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EvidenceUsageRow {
        private Long id;
        private Long applicationId;
        private Long targetJobId;
        private String assetType;
        private Long assetId;
        private String assetVersion;
        private Long packageSnapshotId;
        private String sourceHash;
        private String contentHash;
        private String usageScene;
        private LocalDateTime usedAt;
        private Long hypothesisId;
        private Long variantId;
        private Long assignmentId;
        private LocalDateTime createdAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EvidenceUsageResultRow {
        private Long id;
        private Long usageId;
        private Long applicationId;
        private String eventType;
        private Long eventId;
        private String status;
        private Integer snapshotVersion;
        private Long snapshotId;
        private String outcomeCode;
        private String knownFactsJson;
        private String externalFeedbackText;
        private String userInterpretationText;
        private String unknownsJson;
        private String limitsJson;
        private String sourceType;
        private Long sourceId;
        private String sourceVersion;
        private String sourceHash;
        private LocalDateTime occurredAt;
        private LocalDateTime confirmedAt;
        private String contentHash;
        private Long supersedesSnapshotId;
        private LocalDateTime snapshotCreatedAt;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ManifestFile {
        private String name;
        private long size;
        private String sha256;

        public ManifestFile() {
        }

        public ManifestFile(String name, long size, String sha256) {
            this.name = name;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    @Data
    public static class Manifest {
        private String schemaVersion;
        private Long campaignId;
        private LocalDateTime dataCutoffAt;
        private String sourceHash;
        private List<ManifestFile> files = new ArrayList<>();
        private List<String> missingSections = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    public static class ArchiveResult {
        private byte[] zipBytes;
        private String manifestHash;
        private long fileSize;
    }

    @Data
    public static class SectionLimits {
        private Map<String, Integer> counts = new LinkedHashMap<>();
        private boolean truncated;
    }
}
