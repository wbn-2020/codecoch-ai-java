package com.codecoachai.interview.voicedelivery;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_voice_device_check")
public class VoiceDeviceCheck extends BaseEntity {

    private Long userId;
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
    private String warningCodes;
}
