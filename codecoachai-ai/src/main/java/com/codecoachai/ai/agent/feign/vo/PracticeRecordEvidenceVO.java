package com.codecoachai.ai.agent.feign.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PracticeRecordEvidenceVO {

    private Long id;

    private Long userId;

    private Long questionId;

    private String sourceType;

    private Long sourceId;

    private String reviewStatus;

    private LocalDateTime createdAt;
}
