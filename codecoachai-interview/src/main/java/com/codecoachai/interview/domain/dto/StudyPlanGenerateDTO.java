package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StudyPlanGenerateDTO {

    @NotNull(message = "reportId is required")
    private Long reportId;
    private Long resumeId;
    private Long optimizeRecordId;
    private String targetPosition;
    private String industryDirection;
    private Integer expectedDurationDays;
    private String extraRequirements;
}
