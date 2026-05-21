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
    private JsonNode resultJson;
    private JsonNode fieldPatches;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
