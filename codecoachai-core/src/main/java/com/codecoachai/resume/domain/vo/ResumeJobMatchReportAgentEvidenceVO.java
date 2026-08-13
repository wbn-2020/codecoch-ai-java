package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeJobMatchReportAgentEvidenceVO {

    private Long id;

    private Long userId;

    private Long resumeId;

    private Long resumeVersionId;

    private Long targetJobId;

    private Long jdAnalysisId;

    private String status;

    private LocalDateTime generatedAt;

    private LocalDateTime createdAt;
}
