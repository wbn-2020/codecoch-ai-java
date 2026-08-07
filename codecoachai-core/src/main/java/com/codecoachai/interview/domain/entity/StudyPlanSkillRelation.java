package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_plan_skill_relation")
public class StudyPlanSkillRelation extends BaseEntity {

    private Long userId;
    private Long studyPlanId;
    private Long studyTaskId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long skillGapItemId;
    private String sourceType;
    private Long sourceBizId;
    private Integer priority;
}
