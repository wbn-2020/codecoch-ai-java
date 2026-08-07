package com.codecoachai.interview.streamingasr;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class StreamingAsrSessionCreateDTO {

    private String provider;
    private String language = "zh-CN";
    @Min(8000)
    @Max(48000)
    private Integer sampleRateHz = 16000;
    @Min(1)
    @Max(2)
    private Integer channels = 1;
    private String encoding = "PCM_S16LE";
    @Min(1000)
    @Max(300000)
    private Long timeoutMs = 60000L;
    private String mockTranscript;
    private Boolean mockTimestampsAvailable = Boolean.FALSE;
}
