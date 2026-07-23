package com.codecoachai.interview.voicedelivery;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_voice_delivery_analysis")
public class VoiceDeliveryAnalysis extends BaseEntity {

    private Long userId;
    private Long sessionId;
    private Long voiceSubmissionId;
    private Long deviceCheckId;
    private String taskStatus;
    private String timestampSource;
    private Boolean timestampsAvailable;
    private Long audioDurationMs;
    private Integer wordCount;
    private BigDecimal speakingRatePerMinute;
    private Integer fillerCount;
    private Boolean pauseMetricsAvailable;
    private Integer pauseCount;
    private Long averagePauseMs;
    private Long longestPauseMs;
    private String warningCodes;
    private LocalDateTime deadlineAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String errorCode;
    private String errorMessage;
}
