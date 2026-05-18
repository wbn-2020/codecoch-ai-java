package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SkillGapItemVO {

    private Long id;
    private Long profileId;
    private Long userId;
    private Long targetJobId;
    private String skillName;
    private String category;
    private Integer targetLevel;
    private Integer currentLevel;
    private Integer gapLevel;
    private BigDecimal confidence;
    private String severity;
    private JsonNode evidenceSources;
    private String gapDescription;
    private JsonNode recommendedActions;
    private Integer priority;
    private String sourceType;
    private Long sourceBizId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
