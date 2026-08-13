package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeOptimizeRecordAgentEvidenceVO {

    private Long id;

    private Long userId;

    private Long resumeId;

    private Long targetJobId;

    private String status;

    private LocalDateTime optimizedAt;

    private LocalDateTime createdAt;
}
