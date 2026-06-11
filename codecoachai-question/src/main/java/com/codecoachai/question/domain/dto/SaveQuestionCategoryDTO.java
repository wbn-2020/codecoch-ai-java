package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveQuestionCategoryDTO {

    @NotBlank(message = "请填写分类名称")
    private String categoryName;

    private Long parentId;
    private Integer sort;
    private Integer sortOrder;
    private Integer status;
}
