package com.codecoachai.interview.feign.vo;

import java.util.List;
import lombok.Data;

@Data
public class InnerJobApplicationPackageVO {

    private String id;
    private Long userId;
    private Long targetJobId;
    private Long jobApplicationId;
    private Long jdAnalysisId;
    private Long recommendedResumeVersionId;
    private Long matchReportId;
    private List<Long> projectEvidenceIds;
    private String packageStatus;
    private String resultSource;
    private Boolean fallback;
    private String fallbackReason;
}
