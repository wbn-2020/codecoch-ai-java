package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
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
    private String status;
    private String reportStatus;
    private Long currentStageId;
    private Long currentQuestionId;
    private Long currentQuestionGroupId;
    private Integer answeredQuestionCount;
    private Integer maxQuestionCount;
    private Integer currentFollowUpCount;
    private String failureReason;
}
