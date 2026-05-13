package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_report")
public class InterviewReport extends BaseEntity {

    private Long sessionId;
    private String status;
    private Integer totalScore;
    private String summary;
    private String strengths;
    private String weaknesses;
    private String suggestions;
    private String failureReason;
}
