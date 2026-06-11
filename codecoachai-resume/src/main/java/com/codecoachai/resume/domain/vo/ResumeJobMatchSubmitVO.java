package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeJobMatchSubmitVO {

    private Long reportId;
    private Long resumeId;
    private Long targetJobId;
    private Long jdAnalysisId;
    private Long aiCallLogId;
    private String asyncMessageId;
    private String asyncTraceId;
    private String asyncBizType;
    private String asyncBizId;
    private String asyncSendStatus;
    private String status;
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
