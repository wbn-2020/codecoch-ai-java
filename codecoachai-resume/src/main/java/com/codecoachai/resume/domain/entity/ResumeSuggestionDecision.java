package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_suggestion_decision")
public class ResumeSuggestionDecision extends BaseEntity {
    private Long userId;
    private Long suggestionId;
    private String decisionType;
    private String fromStatus;
    private String toStatus;
    private Integer decisionVersion;
    private Long resultResumeVersionId;
    private String idempotencyKey;
    private String note;
}
