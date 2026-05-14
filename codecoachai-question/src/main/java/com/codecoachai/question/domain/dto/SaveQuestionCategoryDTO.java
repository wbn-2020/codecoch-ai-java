package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveQuestionCategoryDTO {

    @NotBlank(message = "categoryName is required")
    private String categoryName;

    private Long parentId;
    private Integer sort;
    private Integer sortOrder;
    private Integer status;
}
