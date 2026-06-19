package com.codecoachai.resume.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobApplicationSaveDTO {
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
}
