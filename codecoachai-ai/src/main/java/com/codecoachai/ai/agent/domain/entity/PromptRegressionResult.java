package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_regression_result")
public class PromptRegressionResult extends BaseEntity {
    private Long caseId;
    private Long promptVersionId;
    private String status;
    private String outputJson;
    private Integer score;
    private String errorMessage;
}
