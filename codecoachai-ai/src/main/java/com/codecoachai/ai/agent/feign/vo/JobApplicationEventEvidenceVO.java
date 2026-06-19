package com.codecoachai.ai.agent.feign.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobApplicationEventEvidenceVO {

    private Long id;

    private Long userId;

    private Long applicationId;

    private String eventType;

    private LocalDateTime eventTime;
}
