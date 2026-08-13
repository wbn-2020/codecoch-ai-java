package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobApplicationEventAgentEvidenceVO {

    private Long id;

    private Long userId;

    private Long applicationId;

    private String eventType;

    private LocalDateTime eventTime;
}
