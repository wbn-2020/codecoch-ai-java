package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_transcript")
public class InterviewTranscript extends BaseEntity {

    private Long userId;
    private Long sessionId;
    private Long voiceSubmissionId;
    private Long questionMessageId;
    private Long questionId;
    private String draftText;
    private String confirmedText;
    private BigDecimal confidence;
    private String transcriptStatus;
    private String asrProvider;
    private Boolean fallback;
    private String fallbackReason;
    private String traceId;
    private LocalDateTime confirmedAt;
    private Long submittedAnswerMessageId;
    private LocalDateTime submittedAt;
}
