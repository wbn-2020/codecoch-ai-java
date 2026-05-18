package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ResumeJobMatchReportDetailVO {

    private Long reportId;
    private Long userId;
    private Long resumeId;
    private String resumeTitle;
    private Long targetJobId;
    private String jobTitle;
    private String companyName;
    private Long jdAnalysisId;
    private Integer overallScore;
    private Integer techStackScore;
    private Integer projectExperienceScore;
    private Integer businessFitScore;
    private Integer communicationScore;
    private JsonNode strengths;
    private JsonNode gaps;
    private JsonNode resumeRisks;
    private JsonNode optimizationSuggestions;
    private JsonNode recommendedLearningTopics;
    private JsonNode recommendedInterviewTopics;
    private String summary;
    private JsonNode rawResult;
    private String status;
    private String errorMessage;
    private Long aiCallLogId;
    private List<ResumeJobMatchDetailItemVO> details;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
