package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class PromptTemplateVO {

    private Long id;
    private String scene;
    private String name;
    private String templateName;
    private String content;
    private String templateContent;
    private String variables;
    private String version;
    private Integer status;
}
