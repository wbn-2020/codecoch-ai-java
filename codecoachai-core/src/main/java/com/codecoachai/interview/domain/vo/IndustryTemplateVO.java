package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IndustryTemplateVO {

    private Long industryTemplateId;
    private String industryCode;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
