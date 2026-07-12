package com.codecoachai.interview.audioretention;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_audio_retention_record")
public class InterviewAudioRetentionRecord extends BaseEntity {

    private Long userId;
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
