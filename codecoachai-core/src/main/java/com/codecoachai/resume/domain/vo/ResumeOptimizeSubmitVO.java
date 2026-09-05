package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ResumeOptimizeSubmitVO {

    private Long optimizeRecordId;
    private Long resumeId;
    private Long targetJobId;
    private Long aiCallLogId;
    private String asyncMessageId;
    private String asyncTraceId;
    private String asyncBizType;
    private String asyncBizId;
    private String asyncSendStatus;
    private String optimizeStatus;
    private Integer overallScore;
    private String overallComment;
    private JsonNode rewriteSuggestions;
    private JsonNode riskWarnings;
    private JsonNode possibleInterviewQuestions;
    private JsonNode nextActions;
    private String errorMessage;
}
