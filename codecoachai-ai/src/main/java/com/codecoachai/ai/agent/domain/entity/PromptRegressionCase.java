package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_regression_case")
public class PromptRegressionCase extends BaseEntity {
    private String caseName;
    private String promptType;
    private String inputJson;
    private String expectedSchemaJson;
    private Integer enabled;
}
