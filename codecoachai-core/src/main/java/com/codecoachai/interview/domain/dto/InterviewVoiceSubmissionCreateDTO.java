package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class InterviewVoiceSubmissionCreateDTO {

    @NotNull(message = "fileId is required")
    private Long fileId;

    @NotNull(message = "questionMessageId is required")
    private Long questionMessageId;

    private Long questionId;
    @NotNull(message = "audioDurationMs is required")
    @Positive(message = "audioDurationMs must be positive")
    @Max(value = 120000, message = "audioDurationMs must be 120000 milliseconds or less")
    private Long audioDurationMs;
    private String mimeType;
    private String traceId;
}
