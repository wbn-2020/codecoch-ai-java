package com.codecoachai.interview.voicedelivery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class VoiceDeliveryAnalysisCreateDTO {

    @NotNull
    private Long voiceSubmissionId;
    private Long deviceCheckId;
    @Size(max = 10000)
    private String transcript;
    @Min(1)
    @Max(3600000)
    private Long audioDurationMs;
    private String timestampSource = "NONE";
    @Size(max = 5000)
    private List<VoiceWordTimingDTO> wordTimings;
    @Min(100)
    @Max(120000)
    private Long timeoutMs = 10000L;
}
