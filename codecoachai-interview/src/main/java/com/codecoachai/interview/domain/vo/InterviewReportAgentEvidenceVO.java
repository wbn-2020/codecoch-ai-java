package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewReportAgentEvidenceVO {

    private Long id;

    private Long userId;

    private Long sessionId;

    private Long targetJobId;

    private String status;

    private LocalDateTime generatedAt;

    private LocalDateTime createdAt;
}
