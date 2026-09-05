package com.codecoachai.interview.tts;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TtsTaskVO {

    private String taskId;
    private String provider;
    private String status;
    private String contentType;
    private String audioBase64;
    private Long estimatedDurationMs;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime deadlineAt;
    private LocalDateTime completedAt;
}
