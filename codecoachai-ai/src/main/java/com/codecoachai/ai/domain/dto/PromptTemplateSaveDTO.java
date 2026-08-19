package com.codecoachai.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromptTemplateSaveDTO {

    @NotBlank(message = "模板场景不能为空")
    @Size(max = 64, message = "模板场景不能超过 64 个字符")
    @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "模板场景只能使用大写字母、数字和下划线")
    private String scene;

    @Size(max = 128, message = "模板名称不能超过 128 个字符")
    private String name;

    @Size(max = 128, message = "模板名称不能超过 128 个字符")
    private String templateName;

    @Size(max = 500, message = "模板描述不能超过 500 个字符")
    private String description;

    @Size(max = 200000, message = "提示词正文不能超过 200000 个字符")
    private String content;

    @Size(max = 200000, message = "提示词正文不能超过 200000 个字符")
    private String templateContent;

    @Size(max = 20000, message = "变量声明不能超过 20000 个字符")
    private String variables;

    @Size(max = 32, message = "模板版本不能超过 32 个字符")
    private String version;

    private Long activeVersionId;
    private Integer enabled;
    private Integer status;

    private Boolean confirm;
    private Boolean dryRun;

    @Size(max = 500, message = "操作原因不能超过 500 个字符")
    private String reason;

    @Size(max = 200, message = "幂等键不能超过 200 个字符")
    private String idempotencyKey;
    private Integer expectedStatus;
    private Long expectedActiveVersionId;
}
