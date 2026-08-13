package com.codecoachai.task.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminDeadLetterVO {
    private Long id;
    private String messageId;
    private String bizType;
    private String bizId;
    private Long userId;
    private String traceId;
    private String payloadPreview;
    private String payloadHash;
    private String lastFailureReason;
    private Integer totalRetry;
    private String handleStatus;
    private String handleNote;
    private Long handlerUserId;
    private Boolean rawFieldsAvailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
