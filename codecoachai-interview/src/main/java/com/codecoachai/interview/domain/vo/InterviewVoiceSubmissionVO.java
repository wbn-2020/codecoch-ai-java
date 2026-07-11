package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewVoiceSubmissionVO {

    private Long voiceSubmissionId;
    private Long sessionId;
    private Long questionMessageId;
    private Long questionId;
    private Long fileId;
    private Long audioDurationMs;
    private String mimeType;
    private String voiceStatus;
    private String traceId;
    private Boolean fallback;
    private String fallbackReason;
    private String fileDeleteStatus;
    private String fileDeleteReason;
    private LocalDateTime fileDeleteRequestedAt;
    private LocalDateTime fileDeletedAt;
    private InterviewTranscriptVO transcript;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
