package com.codecoachai.interview.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewVoiceTraceVO {

    private Long voiceSubmissionId;
    private Long transcriptId;
    private Long answerMessageId;
    private Long questionMessageId;
    private Long questionId;
    private String answerSource;
    private String transcriptStatus;
    private BigDecimal confidence;
    private Boolean lowConfidence;
    private Boolean fallback;
    private String fallbackReason;
    private String traceId;
    private LocalDateTime confirmedAt;
    private LocalDateTime submittedAt;
}
