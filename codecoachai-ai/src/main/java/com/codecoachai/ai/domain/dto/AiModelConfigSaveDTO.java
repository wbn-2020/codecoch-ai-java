package com.codecoachai.ai.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiModelConfigSaveDTO {

    @NotBlank(message = "供应商标识不能为空")
    @Size(max = 64, message = "供应商标识不能超过 64 个字符")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "供应商标识只能包含字母、数字、点、下划线或连字符")
    private String provider;

    @Size(max = 128, message = "模型标识不能超过 128 个字符")
    private String modelCode;

    @Size(max = 128, message = "模型标识不能超过 128 个字符")
    private String modelName;

    @Size(max = 128, message = "显示名称不能超过 128 个字符")
    private String displayName;

    @Size(max = 512, message = "能力标签不能超过 512 个字符")
    private String capabilityTags;

    @NotBlank(message = "接口地址不能为空")
    @Size(max = 512, message = "接口地址不能超过 512 个字符")
    private String apiBaseUrl;

    @Size(max = 4096, message = "API Key 长度超出限制")
    private String apiKey;

    @DecimalMin(value = "0.0", message = "Temperature 不能小于 0")
    @DecimalMax(value = "2.0", message = "Temperature 不能大于 2")
    private Double temperature;

    @Min(value = 1, message = "最大输出长度不能小于 1")
    @Max(value = 131072, message = "最大输出长度不能超过 131072")
    private Integer maxTokens;

    @Min(value = 0, message = "默认模型标记只能为 0 或 1")
    @Max(value = 1, message = "默认模型标记只能为 0 或 1")
    private Integer defaultModel;

    @Min(value = 0, message = "默认模型标记只能为 0 或 1")
    @Max(value = 1, message = "默认模型标记只能为 0 或 1")
    private Integer isDefault;

    @Min(value = 0, message = "配置状态只能为 0 或 1")
    @Max(value = 1, message = "配置状态只能为 0 或 1")
    private Integer enabled;

    @Min(value = 0, message = "配置状态只能为 0 或 1")
    @Max(value = 1, message = "配置状态只能为 0 或 1")
    private Integer status;

    @Min(value = 0, message = "排序值不能小于 0")
    private Integer sortOrder;

    @Size(max = 512, message = "说明不能超过 512 个字符")
    private String remark;

    @Size(max = 512, message = "说明不能超过 512 个字符")
    private String description;
    private Boolean confirm;
    private Boolean dryRun;

    @Size(max = 500, message = "操作原因不能超过 500 个字符")
    private String reason;

    @Size(max = 200, message = "幂等键不能超过 200 个字符")
    private String idempotencyKey;

}
