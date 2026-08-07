package com.codecoachai.interview.audioretention;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AudioCleanupRequestDTO {

    @Min(100)
    @Max(120000)
    private Long timeoutMs = 15000L;
}
