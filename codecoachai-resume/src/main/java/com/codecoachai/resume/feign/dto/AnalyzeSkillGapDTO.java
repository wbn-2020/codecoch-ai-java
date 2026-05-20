package com.codecoachai.resume.feign.dto;

import lombok.Data;

@Data
public class AnalyzeSkillGapDTO {

    private Long profileId;
    private Long matchReportId;
    private Long userId;
    private Long resumeId;
    private Long targetJobId;
    private Long jdAnalysisId;
    private String targetJobJson;
    private String jobDescriptionAnalysisJson;
    private String matchReportJson;
    private String matchDetailsJson;
    private String gapsJson;
    private String recommendedLearningTopicsJson;
    private String recommendedInterviewTopicsJson;
    private String resumeAnalysisJson;
    private String resumeSnapshotJson;
}
