package com.codecoachai.resume.domain.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * Read-only skill-gap projection served to the AI agent context (V13).
 */
@Data
public class InnerSkillGapAgentContextVO {

    private Long id;
    private Long targetJobId;
    private String skillName;
    private String category;
    private String severity;
    private Integer gapLevel;
    private BigDecimal confidence;
    private String gapDescription;
    private String sourceType;
    private List<String> recommendedActions;
}
