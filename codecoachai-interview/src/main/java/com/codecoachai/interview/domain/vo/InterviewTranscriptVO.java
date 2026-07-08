package com.codecoachai.interview.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewTranscriptVO {

    private Long transcriptId;
    private Long voiceSubmissionId;
    private Long sessionId;
    private Long questionMessageId;
    private Long questionId;
    private String draftText;
    private String confirmedText;
    private BigDecimal confidence;
    private String confidenceLevel;
    private Boolean lowConfidence;
    private String transcriptStatus;
    private String asrProvider;
    private Boolean fallback;
    private String fallbackReason;
    private String traceId;
    private LocalDateTime confirmedAt;
    private Long submittedAnswerMessageId;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
