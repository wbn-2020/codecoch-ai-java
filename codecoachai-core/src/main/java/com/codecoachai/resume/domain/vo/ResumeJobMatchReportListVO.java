package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeJobMatchReportListVO {

    private Long reportId;
    private Long resumeId;
    private String resumeTitle;
    private Long resumeVersionId;
    private Integer resumeVersionNo;
    private String resumeVersionName;
    private Long targetJobId;
    private String jobTitle;
    private String companyName;
    private Integer overallScore;
    private Integer techStackScore;
    private Integer projectExperienceScore;
    private Integer businessFitScore;
    private Integer communicationScore;
    private String status;
    private String summary;
    private String errorMessage;
    private String sourceType;
    private Long sourceId;
    private String trustStatus;
    private String evidenceSummary;
    private Boolean fallback;
    private JsonNode schemaWarnings;
    private Integer schemaWarningCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
