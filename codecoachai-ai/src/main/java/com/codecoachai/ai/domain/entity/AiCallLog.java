package com.codecoachai.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_call_log")
public class AiCallLog extends BaseEntity {

    private Long userId;
    private String scene;
    private String modelName;
    private Long promptTemplateId;
    private String requestPrompt;
    private String responseContent;
    private String businessId;
    private String requestBody;
    private String responseBody;
    private Long elapsedMs;
    private Long costMillis;
    private Integer status;
    private String errorMessage;
}
