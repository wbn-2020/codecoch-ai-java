package com.codecoachai.resume.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class ApplicationPackageSaveDTO {

    private Long targetJobId;
    private Long jdAnalysisId;
    private Long resumeVersionId;
    private Long matchReportId;
    private List<Long> projectEvidenceIds;
    private String packageStatus;
}
