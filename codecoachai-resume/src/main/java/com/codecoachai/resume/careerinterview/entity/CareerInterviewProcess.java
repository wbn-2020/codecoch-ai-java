package com.codecoachai.resume.careerinterview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_interview_process")
public class CareerInterviewProcess extends BaseEntity {
    private Long userId;
    private Long applicationId;
    private String status;
    private Integer currentRoundNo;
    private String outcome;
    private Integer lockVersion;
}
