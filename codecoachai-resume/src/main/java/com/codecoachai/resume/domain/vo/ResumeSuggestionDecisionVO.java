package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeSuggestionDecisionVO {
    private Long id;
    private String decisionType;
    private String fromStatus;
    private String toStatus;
    private Integer decisionVersion;
    private Long resultResumeVersionId;
    private String idempotencyKey;
    private String note;
    private LocalDateTime createdAt;
}
