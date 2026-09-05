package com.codecoachai.ai.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI model live probe result")
public class AiModelProbeVO {

    private Long modelId;
    private String provider;
    private String modelCode;
    private boolean success;
    private String status;
    private String failureType;
    private Integer httpStatus;
    private Long elapsedMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String message;
    private String requestPromptPreview;
    private String responsePreview;
}
