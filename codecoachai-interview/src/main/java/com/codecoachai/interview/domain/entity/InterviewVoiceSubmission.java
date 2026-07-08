package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_voice_submission")
public class InterviewVoiceSubmission extends BaseEntity {

    private Long userId;
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
}
