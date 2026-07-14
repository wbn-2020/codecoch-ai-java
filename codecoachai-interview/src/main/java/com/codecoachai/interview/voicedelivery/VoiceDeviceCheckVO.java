package com.codecoachai.interview.voicedelivery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class VoiceDeviceCheckVO {

    private Long deviceCheckId;
    private Long sessionId;
    private String permissionState;
    private Integer sampleRateHz;
    private Integer channels;
    private Boolean inputDetected;
    private Boolean echoCancellation;
    private Boolean noiseSuppression;
    private Boolean autoGainControl;
    private BigDecimal averageRmsDbfs;
    private BigDecimal clippingRatio;
    private String checkStatus;
    private List<String> warningCodes;
    private LocalDateTime createdAt;
}
