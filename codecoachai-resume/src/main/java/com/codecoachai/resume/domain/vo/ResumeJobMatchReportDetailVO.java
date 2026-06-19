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
    private Long resumeVersionId;
    private Integer resumeVersionNo;
    private String resumeVersionName;
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
    private String status;
    private String errorMessage;
    private Long aiCallLogId;
    private String sourceType;
    private Long sourceId;
    private String trustStatus;
    private String evidenceSummary;
    private Boolean fallback;
    private JsonNode schemaWarnings;
    private Integer schemaWarningCount;
    private String asyncMessageId;
    private String asyncTraceId;
    private String asyncBizType;
    private String asyncBizId;
    private String asyncSendStatus;
    private List<ResumeJobMatchDetailItemVO> details;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
