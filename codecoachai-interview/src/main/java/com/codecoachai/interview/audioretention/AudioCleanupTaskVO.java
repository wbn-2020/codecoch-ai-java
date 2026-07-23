package com.codecoachai.interview.audioretention;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AudioCleanupTaskVO {

    private String cleanupTaskId;
    private Long retentionRecordId;
    private Integer attemptNo;
    private String cleanupStatus;
    private LocalDateTime deadlineAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorCode;
    private String errorMessage;
}
