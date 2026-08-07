package com.codecoachai.resume.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobRequirementVO {

    private Long id;
    private Long targetJobId;
    private Long jdAnalysisId;
    private String requirementKey;
    private String requirementType;
    private String requirementName;
    private String description;
    private String category;
    private String requiredLevel;
    private String priority;
    private BigDecimal weight;
    private String confidenceLevel;
    private String sourceField;
    private Boolean sourceFallback;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
