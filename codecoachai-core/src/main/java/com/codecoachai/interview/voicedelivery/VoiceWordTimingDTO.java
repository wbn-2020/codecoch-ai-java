package com.codecoachai.interview.voicedelivery;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VoiceWordTimingDTO {

    @NotBlank
    private String text;
    @NotNull
    @Min(0)
    private Long startMs;
    @NotNull
    @Min(0)
    private Long endMs;
}
