package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InterviewVoiceSubmissionCreateDTO {

    @NotNull(message = "fileId is required")
    private Long fileId;

    @NotNull(message = "questionMessageId is required")
    private Long questionMessageId;

    private Long questionId;
    private Long audioDurationMs;
    private String mimeType;
    private String traceId;
}
