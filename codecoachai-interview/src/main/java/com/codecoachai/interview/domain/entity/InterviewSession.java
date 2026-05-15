package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_session")
public class InterviewSession extends BaseEntity {

    private Long userId;
    private Long resumeId;
    private String mode;
    private String title;
    private String targetPosition;
    private String experienceLevel;
    private Long industryTemplateId;
    private String industryDirection;
    private String industryContext;
    private String difficulty;
    private String interviewerStyle;
    private Boolean basedOnResume;
    private String status;
    private String reportStatus;
    private Long currentStageId;
    private Long currentQuestionId;
    private Long currentQuestionGroupId;
    private Integer answeredQuestionCount;
    private Integer maxQuestionCount;
    private Integer currentFollowUpCount;
    private Integer totalScore;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String failureReason;
}
