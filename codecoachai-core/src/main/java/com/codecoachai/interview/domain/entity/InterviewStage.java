package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_stage")
public class InterviewStage extends BaseEntity {

    private Long sessionId;
    private String stageType;
    private String stageName;
    private Integer sort;
    private Integer stageOrder;
    private Integer expectedQuestionCount;
    private Integer askedQuestionCount;
    private String focusPoints;
    private Boolean basedOnResume;
    private Boolean allowFollowUp;
    private Integer maxFollowUpCount;
    private String status;
    private Integer score;
}
