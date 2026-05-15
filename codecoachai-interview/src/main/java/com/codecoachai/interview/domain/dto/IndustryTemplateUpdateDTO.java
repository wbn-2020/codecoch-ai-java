package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IndustryTemplateUpdateDTO {

    @NotBlank
    private String industryCode;

    @NotBlank
    private String industryName;

    private String description;
    private String targetPositions;
    private String coreBusinessScenarios;
    private String keyTechnicalPoints;
    private String commonQuestionDirections;
    private String riskPoints;
    private String promptContext;
    private Integer enabled;
    private Integer sortOrder;
}
