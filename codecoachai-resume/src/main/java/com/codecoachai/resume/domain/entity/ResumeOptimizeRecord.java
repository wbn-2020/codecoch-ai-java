package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_optimize_record")
public class ResumeOptimizeRecord extends BaseEntity {

    private Long userId;
    private Long resumeId;
    private String targetPosition;
    private Integer experienceYears;
    private String industryDirection;
    private String requestJson;
    private String resultJson;
    private String optimizeStatus;
    private String errorMessage;
    private Long aiCallLogId;
}
