package com.codecoachai.task.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminAsyncTaskVO {
    private Long id;
    private String messageId;
    private String bizType;
    private String bizId;
    private Long userId;
    private String traceId;
    private String status;
    private String executionId;
    private String parentExecutionId;
    private Long runId;
    private Integer attemptNo;
    private String idempotencyKey;
    private String terminalReasonCode;
    private Integer retryCount;
    private Integer maxRetry;
    private Integer maxRetryCount;
    private String failureReason;
    private String payloadPreview;
    private String payloadHash;
    private String resultPreview;
    private String resultHash;
    private Boolean rawFieldsAvailable;
    private String governanceStatus;
    private String governanceReason;
    private String governanceOwner;
    private String retryPreviewHash;
    private String failureClass;
    private Long ageMinutes;
    private LocalDateTime governanceUpdatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
