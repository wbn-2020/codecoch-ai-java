package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_plan")
public class StudyPlan extends BaseEntity {

    private Long userId;
    private String sourceType;
    private Long sourceId;
    private Long reportId;
    private Long sessionId;
    private Long resumeId;
    private Long optimizeRecordId;
    private String targetPosition;
    private String industryDirection;
    private String planTitle;
    private String planSummary;
    private String planStatus;
    private Integer durationDays;
    private Long aiCallLogId;
    private String requestJson;
    private String resultJson;
    private String failureReason;
}
