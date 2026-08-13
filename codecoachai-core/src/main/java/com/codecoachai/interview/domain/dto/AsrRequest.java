package com.codecoachai.interview.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AsrRequest {

    private Long userId;
    private Long sessionId;
    private Long voiceSubmissionId;
    private Long fileId;
    private String bizType;
    private String mimeType;
    private Long audioDurationMs;
    private String language;
    private String scene;
    private String requestId;
    private String traceId;
}
