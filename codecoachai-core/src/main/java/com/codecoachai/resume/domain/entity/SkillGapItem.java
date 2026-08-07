package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skill_gap_item")
public class SkillGapItem extends BaseEntity {

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
    private String evidenceSourcesJson;
    private String gapDescription;
    private String recommendedActionsJson;
    private Integer priority;
    private String sourceType;
    private Long sourceBizId;
}
