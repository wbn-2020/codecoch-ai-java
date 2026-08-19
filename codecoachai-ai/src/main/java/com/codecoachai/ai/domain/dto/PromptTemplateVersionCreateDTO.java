package com.codecoachai.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromptTemplateVersionCreateDTO {

    @NotBlank(message = "版本号不能为空")
    @Size(max = 64, message = "版本号不能超过 64 个字符")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
            message = "版本号只能包含字母、数字、点、下划线或连字符")
    private String versionCode;

    @Size(max = 128, message = "版本名称不能超过 128 个字符")
    private String versionName;

    @NotBlank(message = "提示词正文不能为空")
    @Size(max = 200000, message = "提示词正文不能超过 200000 个字符")
    private String content;

    @Size(max = 20000, message = "变量声明不能超过 20000 个字符")
    private String variablesJson;

    @Size(max = 20000, message = "模型参数不能超过 20000 个字符")
    private String modelParamsJson;

    @Pattern(regexp = "(?i)DRAFT|INACTIVE|DISABLED", message = "新版本状态只能为 DRAFT、INACTIVE 或 DISABLED")
    private String status;

    @Size(max = 1000, message = "变更说明不能超过 1000 个字符")
    private String changeLog;

    private Boolean confirm;
    private Boolean dryRun;

    @Size(max = 500, message = "操作原因不能超过 500 个字符")
    private String reason;

    @Size(max = 200, message = "幂等键不能超过 200 个字符")
    private String idempotencyKey;
}
