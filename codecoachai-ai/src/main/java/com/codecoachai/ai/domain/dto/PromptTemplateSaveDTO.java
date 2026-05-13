package com.codecoachai.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromptTemplateSaveDTO {

    @NotBlank(message = "scene is required")
    private String scene;

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "content is required")
    private String content;

    private Integer status;
}
