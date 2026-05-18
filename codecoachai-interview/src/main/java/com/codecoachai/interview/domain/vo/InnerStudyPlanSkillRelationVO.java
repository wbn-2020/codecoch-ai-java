package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InnerStudyPlanSkillRelationVO {

    private Long id;
    private Long userId;
    private Long studyPlanId;
    private Long studyTaskId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long skillGapItemId;
    private String sourceType;
    private Long sourceBizId;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
