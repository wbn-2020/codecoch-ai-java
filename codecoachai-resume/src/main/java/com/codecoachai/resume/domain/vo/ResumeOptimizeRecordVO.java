package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeOptimizeRecordVO {

    private Long optimizeRecordId;
    private Long resumeId;
    private String targetPosition;
    private Integer experienceYears;
    private String industryDirection;
    private String optimizeStatus;
    private String summary;
    private String overallComment;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
