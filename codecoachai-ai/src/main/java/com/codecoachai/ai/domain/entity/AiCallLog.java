package com.codecoachai.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
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
    private String requestBody;
    private String responseBody;
    private Long elapsedMs;
    private Long costMillis;
    private Integer success;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer status;
    private String errorMessage;
    /** V3 新增：路由轨迹，如 deepseek 或 deepseek -> dashscope */
    private String routeTrace;
    /** V3 新增：预估费用（元） */
    private Double estimatedCost;
    /** V3_010 兼容字段：旧迁移使用 token_cost 保存成本。 */
    @TableField("token_cost")
    private Double tokenCost;
    /** V3_010 兼容字段：旧迁移使用 model 保存模型标识。 */
    @TableField("model")
    private String model;
}
