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
    private String traceId;
    private String inputVariablesJson;
    private String modelParamsJson;
    private String promptHash;
    private String responseFormat;
    private String requestPrompt;
    private String responseContent;
    private String businessId;
    private Long elapsedMs;
    private Long costMillis;
    private Integer success;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer status;
    private String errorMessage;
    private String requestBody;
    private String responseBody;
    private LocalDateTime createdAt;
}
