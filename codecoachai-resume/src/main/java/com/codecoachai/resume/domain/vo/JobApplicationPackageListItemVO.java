package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobApplicationPackageListItemVO {

    private Long id;
    private String packageNo;
    private Long targetJobId;
    private Long jdAnalysisId;
    private Long recommendedResumeVersionId;
    private Long matchReportId;
    private Long jobApplicationId;
    private String companyName;
    private String jobTitle;
    private String readinessLevel;
    private Integer readinessScore;
    private String readinessReason;
    private String packageStatus;
    private String resultSource;
    private Boolean fallback;
    private String traceId;
    private Integer snapshotVersion;
    private LocalDateTime refreshedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
