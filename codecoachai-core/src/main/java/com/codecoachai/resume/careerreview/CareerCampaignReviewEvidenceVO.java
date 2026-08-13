package com.codecoachai.resume.careerreview;

import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileModels.OperatingProfileView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CareerCampaignReviewEvidenceVO {

    public static final String EVIDENCE_SCHEMA_VERSION =
            "V7_CAMPAIGN_REVIEW_EVIDENCE_V1";

    private Long userId;
    private Long campaignId;
    private String campaignStatus;
    private String campaignTitle;
    private Boolean completed;
    private Boolean allOpportunitiesClosed;
    private Integer sampleSize;
    private LocalDateTime dataCutoffAt;
    private String evidenceSchemaVersion = EVIDENCE_SCHEMA_VERSION;
    private String evidenceHash;
    private CampaignSummary campaign;
    private OperatingProfileView operatingProfile;
    private List<ApplicationEvidence> applications = new ArrayList<>();
    private List<EventEvidence> recentEvents = new ArrayList<>();
    private List<CalendarEvidence> upcomingCalendar = new ArrayList<>();
    private List<InterviewEvidence> interviews = new ArrayList<>();
    private List<OfferEvidence> offers = new ArrayList<>();
    private List<ActivityEvidence> activities = new ArrayList<>();
    private List<ResearchEvidence> research = new ArrayList<>();
    private List<MaterialEvidence> materials = new ArrayList<>();
    private List<Fact> facts = new ArrayList<>();
    private List<Source> sources = new ArrayList<>();
    private Map<String, Coverage> coverage = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class CampaignSummary {
        private Long id;
        private String name;
        private String status;
        private String goal;
    }

    @Data
    public static class ApplicationEvidence {
        private Long id;
        private Long applicationId;
        private String companyName;
        private String jobTitle;
        private String status;
        private String stage;
        private String priorityLevel;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime nextFollowUpAt;
        private LocalDateTime interviewAt;
        private LocalDateTime offerDeadlineAt;
        private Boolean interviewPrepMissing;
        private Boolean interviewReviewMissing;
        private Boolean materialCoverageLow;
        private Boolean researchCoverageLow;
        private LocalDateTime contactFollowUpAt;
        private String sourceHash;
        private List<Source> sources = new ArrayList<>();
    }

    @Data
    public static class EventEvidence {
        private Long id;
        private Long applicationId;
        private String sourceType;
        private String eventType;
        private LocalDateTime eventTime;
        private String summary;
        private String sourceHash;
    }

    @Data
    public static class CalendarEvidence {
        private Long id;
        private Long applicationId;
        private String title;
        private String eventType;
        private LocalDateTime startsAtUtc;
        private LocalDateTime endsAtUtc;
        private String timezone;
        private String status;
        private String sourceHash;
    }

    @Data
    public static class InterviewEvidence {
        private Long id;
        private Long applicationId;
        private String status;
        private Integer currentRoundNo;
        private String outcome;
        private Integer roundCount;
        private LocalDateTime nextInterviewAt;
        private String sourceHash;
    }

    @Data
    public static class OfferEvidence {
        private Long id;
        private Long applicationId;
        private String status;
        private LocalDateTime decisionDeadline;
        private LocalDateTime finalizedAt;
        private String sourceHash;
    }

    @Data
    public static class ActivityEvidence {
        private Long id;
        private Long applicationId;
        private Long contactId;
        private String activityType;
        private String channelType;
        private String status;
        private LocalDateTime occurredAt;
        private LocalDateTime nextFollowUpAt;
        private String sourceHash;
    }

    @Data
    public static class ResearchEvidence {
        private Long id;
        private Long applicationId;
        private String confidenceLevel;
        private String sourceSetHash;
        private String sourceHash;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class MaterialEvidence {
        private Long id;
        private Long applicationId;
        private String packageStatus;
        private String readinessLevel;
        private Integer readinessScore;
        private String sourceHash;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class Coverage {
        private Boolean available = true;
        private Integer itemCount = 0;
        private Boolean truncated = false;
        private String reason;
    }

    @Data
    public static class Fact {
        private String key;
        private String label;
        private Object value;
        private String sourceRef;
    }

    @Data
    public static class Source {
        private String sourceType;
        private Long sourceId;
        private Integer sourceVersion;
        private LocalDateTime sourceTime;
        private LocalDateTime sourceUpdatedAt;
        private String sourceHash;
        private Long applicationId;
        private Long campaignId;
        private LocalDateTime observedAt;
        private String fieldPath;
        private String summary;
    }
}
