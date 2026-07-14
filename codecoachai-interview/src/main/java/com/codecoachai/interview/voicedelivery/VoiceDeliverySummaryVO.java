package com.codecoachai.interview.voicedelivery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class VoiceDeliverySummaryVO {

    private Long sessionId;
    private Long analysisId;
    private Boolean available;
    private String status;
    private String missingReason;
    private Long audioDurationMs;
    private Integer wordCount;
    private BigDecimal speakingRatePerMinute;
    private Integer fillerCount;
    private Boolean pauseMetricsAvailable;
    private Integer pauseCount;
    private Long averagePauseMs;
    private Long longestPauseMs;
    private List<String> warningCodes;
    private LocalDateTime completedAt;
}
