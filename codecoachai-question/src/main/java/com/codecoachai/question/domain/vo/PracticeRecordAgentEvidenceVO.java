package com.codecoachai.question.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PracticeRecordAgentEvidenceVO {

    private Long id;

    private Long userId;

    private Long questionId;

    private String sourceType;

    private Long sourceId;

    private String reviewStatus;

    private LocalDateTime createdAt;
}
