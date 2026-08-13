package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeAnalysisResultVO {

    private Long analysisRecordId;
    private Long fileId;
    private Long resumeId;
    private String parseStatus;
    private String errorMessage;
    private JsonNode structuredJson;
    private String rawTextSummary;
    private LocalDateTime updatedAt;
}
