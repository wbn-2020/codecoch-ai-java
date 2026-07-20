package com.codecoachai.resume.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class WeeklyCareerEvidenceVO {

    private Long userId;
    private List<ApplicationItem> applications = new ArrayList<>();
    private List<ApplicationEventItem> applicationEvents = new ArrayList<>();
    private List<CalendarEventItem> calendarEvents = new ArrayList<>();
    private List<ExperimentItem> experiments = new ArrayList<>();
    private Map<String, Integer> sourceCounts = new LinkedHashMap<>();
    private String consistencyLevel;
    private Boolean truncated;
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class ApplicationItem {

        private Long applicationId;
        private Long targetJobId;
        private Long resumeVersionId;
        private String channelKey;
        private String source;
        private LocalDateTime appliedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String currentStatus;
        private Boolean included;
        private String excludeReason;
        private String sourceHash;
        private String safeSummary;
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }

    @Data
    public static class ApplicationEventItem {

        private Long eventId;
        private Long applicationId;
        private Long targetJobId;
        private String eventType;
        private LocalDateTime eventTime;
        private LocalDateTime updatedAt;
        private Boolean structuredReview;
        private Boolean included;
        private String excludeReason;
        private String sourceHash;
        private String safeSummary;
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }

    @Data
    public static class CalendarEventItem {

        private Long eventId;
        private Long applicationId;
        private Long targetJobId;
        private String eventType;
        private LocalDateTime startsAtUtc;
        private LocalDateTime endsAtUtc;
        private LocalDateTime updatedAt;
        private String status;
        private String sourceType;
        private Boolean included;
        private String excludeReason;
        private String sourceHash;
        private String safeSummary;
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }

    @Data
    public static class ExperimentItem {

        private Long experimentId;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status;
        private String targetDirection;
        private Boolean demo;
        private Boolean included;
        private String excludeReason;
        private String sourceHash;
        private String safeSummary;
        private List<ExperimentRelationItem> relations = new ArrayList<>();
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }

    @Data
    public static class ExperimentRelationItem {

        private Long relationId;
        private String relationType;
        private Long relationObjectId;
        private Long targetJobId;
        private LocalDateTime sourceTime;
        private Boolean included;
        private String excludeReason;
        private String sourceHash;
    }
}
