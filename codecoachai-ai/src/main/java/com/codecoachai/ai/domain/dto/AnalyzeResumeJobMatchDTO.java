package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class AnalyzeResumeJobMatchDTO {

    private Long reportId;
    private Long userId;
    private Long resumeId;
    private Long resumeVersionId;
    private Long targetJobId;
    private Long jdAnalysisId;
    private String resumeAnalysisJson;
    private String resumeSnapshotJson;
    private String jobDescriptionAnalysisJson;
    private String targetJobJson;
    private String userExperienceYears;
}
