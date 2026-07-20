package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobApplicationVO {
    private Long id;
    private Long campaignId;
    private Long targetJobId;
    private Long resumeVersionId;
    private Long resumeId;
    private Integer resumeVersionNo;
    private String resumeVersionName;
    private Integer resumeVersionCurrentFlag;
    private Long matchReportId;
    private String companyName;
    private String jobTitle;
    private String source;
    private String status;
    private LocalDateTime stageChangedAt;
    private String priorityLevel;
    private String opportunityOutcome;
    private Integer lockVersion;
    private LocalDateTime appliedAt;
    private LocalDateTime nextFollowUpAt;
    private String note;
    private Long latestEventId;
    private String latestEventType;
    private LocalDateTime latestEventTime;
    private String latestEventSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
