package com.codecoachai.interview.tts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TtsTaskCreateDTO {

    @NotBlank
    @Size(max = 2000)
    private String text;
    private String provider;
    private String voice = "mock-default";
    private String locale = "zh-CN";
    private String audioFormat = "mp3";
    @Min(100)
    @Max(120000)
    private Long timeoutMs = 15000L;
}
