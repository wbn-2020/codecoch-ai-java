package com.codecoachai.interview.voicedelivery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class VoiceDeliveryAnalysisVO {

    private Long analysisId;
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
    private List<String> warningCodes;
    private LocalDateTime deadlineAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String errorCode;
    private String errorMessage;
}
