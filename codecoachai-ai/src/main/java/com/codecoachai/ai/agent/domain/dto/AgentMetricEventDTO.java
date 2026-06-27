package com.codecoachai.ai.agent.domain.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class AgentMetricEventDTO {

    private String eventCode;
    private String idempotencyKey;
    private Long userId;
    private Long taskId;
    private Long runId;
    private LocalDate planDate;
    private Long targetJobId;
    private String requestId;
    private String sourcePage;
    private String targetPath;
    private String notificationId;
    private String bizType;
    private String bizId;
    private LocalDateTime occurredAt;
    private Map<String, Object> metadata;
}
