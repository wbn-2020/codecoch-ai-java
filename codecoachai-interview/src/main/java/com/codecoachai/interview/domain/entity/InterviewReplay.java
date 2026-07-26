package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_replay")
public class InterviewReplay extends BaseEntity {

    private Long userId;
    private Long sourceSessionId;
    private Long sourceReportId;
    private Long targetSessionId;
    private Long targetJobId;
    private Long scenarioVersionId;
    private String rubricVersion;
    private String status;
    private String idempotencyKey;
}
