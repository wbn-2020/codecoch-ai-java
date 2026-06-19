package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobApplicationVO {
    private Long id;
    private Long targetJobId;
    private Long resumeVersionId;
    private Long matchReportId;
    private String companyName;
    private String jobTitle;
    private String source;
    private String status;
    private LocalDateTime appliedAt;
    private LocalDateTime nextFollowUpAt;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
