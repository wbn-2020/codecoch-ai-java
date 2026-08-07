package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StudyPlanSkillRelationVO {

    private Long id;
    private Long studyPlanId;
    private Long studyTaskId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long skillGapItemId;
    private String skillName;
    private String category;
    private String severity;
    private String sourceType;
    private Long sourceBizId;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
