package com.codecoachai.interview.voicedelivery;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoiceDeliveryMetrics {

    int wordCount;
    BigDecimal speakingRatePerMinute;
    int fillerCount;
    boolean timestampsAvailable;
    boolean pauseMetricsAvailable;
    Integer pauseCount;
    Long averagePauseMs;
    Long longestPauseMs;
    List<String> warningCodes;
}
