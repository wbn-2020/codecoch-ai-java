package com.codecoachai.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
public class AiModelConfig extends BaseEntity {

    private String provider;
    private String modelCode;
    private String modelName;
    private String capabilityTags;
    private String apiBaseUrl;
    @JsonIgnore
    private String apiKey;
    private Double temperature;
    private Integer maxTokens;
    private Integer defaultModel;
    private Integer enabled;
    private Integer sortOrder;
    private String remark;
    @TableField(exist = false)
    private String apiKeyMasked;
}
