package com.codecoachai.interview.voicedelivery;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class VoiceDeviceCheckCreateDTO {

    @NotBlank
    private String permissionState;
    @Min(8000)
    @Max(192000)
    private Integer sampleRateHz;
    @Min(1)
    @Max(8)
    private Integer channels;
    @NotNull
    private Boolean inputDetected;
    private Boolean echoCancellation;
    private Boolean noiseSuppression;
    private Boolean autoGainControl;
    @DecimalMin("-120.0")
    @DecimalMax("0.0")
    private BigDecimal averageRmsDbfs;
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal clippingRatio;
}
