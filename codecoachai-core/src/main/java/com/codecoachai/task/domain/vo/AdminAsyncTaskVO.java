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
    private Integer retryCount;
    private Integer maxRetry;
    private Integer maxRetryCount;
    private String failureReason;
    private String payloadPreview;
    private String payloadHash;
    private String resultPreview;
    private String resultHash;
    private Boolean rawFieldsAvailable;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
