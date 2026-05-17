package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ResumeOptimizeSubmitVO {

    private Long optimizeRecordId;
    private Long resumeId;
    private Long aiCallLogId;
    private String optimizeStatus;
    private JsonNode resultJson;
    private String errorMessage;
}
