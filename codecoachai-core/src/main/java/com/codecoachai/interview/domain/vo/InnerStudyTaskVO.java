package com.codecoachai.interview.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InnerStudyTaskVO {

    private Long id;
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
    private Integer estimatedMinutes;
    private String acceptanceCriteria;
    private String taskStatus;
    private String relatedQuestionIdsJson;
    private String relatedTagsJson;
    private String resourcesJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
