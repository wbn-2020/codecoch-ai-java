package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class AiModelConfigSaveDTO {

    private String provider;
    private String modelCode;
    private String modelName;
    private String displayName;
    private String capabilityTags;
    private Integer defaultModel;
    private Integer isDefault;
    private Integer enabled;
    private Integer status;
    private Integer sortOrder;
    private String remark;
    private String description;
}
