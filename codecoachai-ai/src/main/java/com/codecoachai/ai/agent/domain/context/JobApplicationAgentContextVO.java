package com.codecoachai.ai.agent.domain.context;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobApplicationAgentContextVO {

    private Long id;
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
    private LocalDateTime appliedAt;
    private LocalDateTime nextFollowUpAt;
    private Boolean followUpOverdue;
    private Boolean followUpDueToday;
    private Long daysUntilFollowUp;
    private String note;
    private Long latestEventId;
    private String latestEventType;
    private LocalDateTime latestEventTime;
    private String latestEventSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
