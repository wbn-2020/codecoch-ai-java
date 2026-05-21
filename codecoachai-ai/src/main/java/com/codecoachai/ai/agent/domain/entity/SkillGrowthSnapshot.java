package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skill_growth_snapshot")
public class SkillGrowthSnapshot extends BaseEntity {
    private Long userId;
    private LocalDate snapshotDate;
    private String skillCode;
    private String skillName;
    private Integer score;
    private Integer taskCount;
    private Integer doneCount;
    private String sourceType;
    private Long sourceId;
}
