package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_task")
public class StudyTask extends BaseEntity {

    private Long planId;
    private Long userId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long skillGapItemId;
    private String sourceType;
    private Long sourceBizId;
    private Integer stageNo;
    private LocalDate plannedDate;
    private String stageTitle;
    private Integer taskOrder;
    private String knowledgePoint;
    private String taskTitle;
    private String taskDescription;
    private String taskType;
    private String priority;
    private Integer estimatedHours;
    private Integer estimatedMinutes;
    private String acceptanceCriteria;
    private String taskStatus;
    private String relatedQuestionIdsJson;
    private String relatedTagsJson;
    private String resourcesJson;
}
