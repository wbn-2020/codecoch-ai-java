package com.codecoachai.task.feign.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentDailyPlanVO {

    private Long runId;
    private String executionId;
    private String parentExecutionId;
    private String idempotencyKey;
    private Integer attemptNo;
    private String executionStatus;
    private Long targetJobId;
    private LocalDate date;
    private String status;
    private String errorCode;
    private String errorMessage;
    private String terminalReasonCode;
    private Boolean consumable;
    private String executionSource;
    private String deliveryQuality;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
