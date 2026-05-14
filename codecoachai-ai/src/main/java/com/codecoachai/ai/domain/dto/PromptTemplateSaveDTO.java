package com.codecoachai.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromptTemplateSaveDTO {

    @NotBlank(message = "scene is required")
    private String scene;

    private String name;
    private String templateName;

    private String content;
    private String templateContent;
    private String variables;
    private String version;

    private Integer status;
}
