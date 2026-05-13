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
    private String status;
}
