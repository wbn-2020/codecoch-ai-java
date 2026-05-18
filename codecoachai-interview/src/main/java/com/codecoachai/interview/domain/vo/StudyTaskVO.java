package com.codecoachai.interview.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class StudyTaskVO {

    private Long id;
    private Long planId;
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
    private List<Long> relatedQuestionIds;
    private List<String> relatedTags;
    private List<String> resources;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
