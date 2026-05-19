package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class AiModelConfigSaveDTO {

    private String provider;
    private String modelCode;
    private String modelName;
    private String capabilityTags;
    private Integer defaultModel;
    private Integer enabled;
    private Integer sortOrder;
    private String remark;
}
