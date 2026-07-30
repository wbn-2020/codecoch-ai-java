package com.codecoachai.ai.agent.domain.context;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * Local mirror of the resume-side InnerSkillGapAgentContextVO (V13).
 */
@Data
public class SkillGapAgentContextVO {

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
