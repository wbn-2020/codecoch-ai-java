package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class PromptTemplateVersionQueryDTO {

    private String status;
    private Integer isActive;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
