package com.codecoachai.interview.audioretention;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AudioRetentionVO {

    private Long retentionRecordId;
    private Long sessionId;
    private Long voiceSubmissionId;
    private Long fileId;
    private String policyCode;
    private LocalDateTime retainUntil;
    private Boolean legalHold;
    private String retentionStatus;
    private LocalDateTime cleanupRequestedAt;
    private LocalDateTime cleanupCompletedAt;
    private String lastErrorCode;
    private String lastErrorMessage;
}
