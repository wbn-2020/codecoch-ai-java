package com.codecoachai.ai.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiCallLogVO {

    private Long id;
    private Long userId;
    private String scene;
    private String modelName;
    private Long promptTemplateId;
    private Long promptTemplateVersionId;
    private String promptVersion;
    private String requestId;
    private String shortRequestId;
    private String traceId;
    private String traceIdShort;
    private String shortTraceId;
    private String routeTrace;
    private String resultSource;
    private String resultSourceLabel;
    private Boolean fallback;
    private Double estimatedCost;
    private String inputVariablesJson;
    private String inputVariablesPreview;
    private String inputVariablesHash;
    private String modelParamsJson;
    private String modelParamsPreview;
    private String promptHash;
    private String responseFormat;
    private String requestPrompt;
    private String requestPromptPreview;
    private String requestPromptHash;
    private String requestPreview;
    private String responseContent;
    private String responseContentPreview;
    private String responseContentHash;
    private String responsePreview;
    private String businessId;
    private Long elapsedMs;
    private Long costMillis;
    private Integer success;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer status;
    private String errorMessage;
    private String errorMessagePreview;
    private String requestBody;
    private String requestBodyPreview;
    private String requestBodyHash;
    private String responseBody;
    private String responseBodyPreview;
    private String responseBodyHash;
    private Boolean rawFieldsAvailable;
    private Boolean rawFieldsIncluded;
    private String rawAccessPermission;
    private LocalDateTime createdAt;
}
