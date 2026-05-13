package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class PromptTemplateVO {

    private Long id;
    private String scene;
    private String name;
    private String content;
    private Integer status;
}
