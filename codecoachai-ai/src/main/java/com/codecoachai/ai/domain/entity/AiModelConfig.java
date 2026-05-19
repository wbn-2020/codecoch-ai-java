package com.codecoachai.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
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
    private Integer defaultModel;
    private Integer enabled;
    private Integer sortOrder;
    private String remark;
}
