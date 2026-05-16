package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_task")
public class StudyTask extends BaseEntity {

    private Long planId;
    private Long userId;
    private Integer stageNo;
    private String stageTitle;
    private Integer taskOrder;
    private String knowledgePoint;
    private String taskTitle;
    private String taskDescription;
    private String taskType;
    private String priority;
    private Integer estimatedHours;
    private String taskStatus;
    private String relatedQuestionIdsJson;
    private String relatedTagsJson;
    private String resourcesJson;
}
