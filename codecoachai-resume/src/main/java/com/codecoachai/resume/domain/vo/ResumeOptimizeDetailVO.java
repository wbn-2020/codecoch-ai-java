package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeOptimizeDetailVO {

    private Long optimizeRecordId;
    private Long resumeId;
    private String targetPosition;
    private Integer experienceYears;
    private String industryDirection;
    private String optimizeStatus;
    private String summary;
    private Integer overallScore;
    private String overallComment;
    private JsonNode targetPositionMatch;
    private JsonNode sectionScores;
    private JsonNode problems;
    private JsonNode rewriteSuggestions;
    private JsonNode riskWarnings;
    private JsonNode possibleInterviewQuestions;
    private JsonNode nextActions;
    private JsonNode fieldPatches;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
