package com.codecoachai.resume.domain.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ApplicationPackageCreateApplicationDTO {

    private Long targetJobId;
    private Long jdAnalysisId;
    private Long resumeVersionId;
    private Long matchReportId;
    private List<Long> projectEvidenceIds;
    private String companyName;
    private String jobTitle;
    private String source;
    private String status;
    private LocalDateTime appliedAt;
    private LocalDateTime nextFollowUpAt;
    private String note;
}
