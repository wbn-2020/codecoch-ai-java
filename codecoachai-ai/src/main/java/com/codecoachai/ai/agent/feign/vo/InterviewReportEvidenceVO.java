package com.codecoachai.ai.agent.feign.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewReportEvidenceVO {

    private Long id;

    private Long userId;

    private Long sessionId;

    private Long targetJobId;

    private String status;

    private LocalDateTime generatedAt;

    private LocalDateTime createdAt;
}
