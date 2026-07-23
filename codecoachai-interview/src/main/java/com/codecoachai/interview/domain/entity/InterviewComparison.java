package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_comparison")
public class InterviewComparison extends BaseEntity {

    private Long userId;
    private Long targetJobId;
    private String reportIds;
    private String reportKey;
    private String rubricVersion;
    private String status;
    private String reasonCodes;
    private String resultJson;
    private String idempotencyKey;
}
