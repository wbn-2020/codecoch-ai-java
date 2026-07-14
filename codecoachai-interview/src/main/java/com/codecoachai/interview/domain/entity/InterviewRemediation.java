package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_remediation")
public class InterviewRemediation extends BaseEntity {

    private Long userId;
    private Long sourceReportId;
    private Long sourceSessionId;
    private Long targetSessionId;
    private Long targetJobId;
    private String sourceRequirementIds;
    private String practicePurpose;
    private String remediationStrength;
    private String rubricVersion;
    private String status;
    private String idempotencyKey;
}
