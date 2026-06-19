package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class AiModelConfigSaveDTO {

    private String provider;
    private String modelCode;
    private String modelName;
    private String displayName;
    private String capabilityTags;
    private String apiBaseUrl;
    private String apiKey;
    private Double temperature;
    private Integer maxTokens;
    private Integer defaultModel;
    private Integer isDefault;
    private Integer enabled;
    private Integer status;
    private Integer sortOrder;
    private String remark;
    private String description;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
