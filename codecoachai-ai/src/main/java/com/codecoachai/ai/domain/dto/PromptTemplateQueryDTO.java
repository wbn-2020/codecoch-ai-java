package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class PromptTemplateQueryDTO {

    private String keyword;
    private String scene;
    private Integer enabled;
    private Integer status;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
