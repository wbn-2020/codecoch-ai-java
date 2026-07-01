package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ApplicationInsightItemVO {
    private Long applicationId;
    private Long resumeVersionId;
    private Long resumeId;
    private Integer resumeVersionNo;
    private String resumeVersionName;
    private Integer resumeVersionCurrentFlag;
    private String companyName;
    private String jobTitle;
    private String status;
    private LocalDateTime appliedAt;
    private LocalDateTime nextFollowUpAt;
    private Long latestEventId;
    private String latestEventType;
    private LocalDateTime latestEventTime;
    private String latestEventSummary;
    private Boolean hasFollowUp;
    private Boolean hasInterview;
    private Boolean hasOffer;
    private Boolean terminal;
}
