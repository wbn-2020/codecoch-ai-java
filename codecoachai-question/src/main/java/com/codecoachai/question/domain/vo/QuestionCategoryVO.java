package com.codecoachai.question.domain.vo;

import lombok.Data;

@Data
public class QuestionCategoryVO {

    private Long id;
    private String categoryName;
    private Integer sort;
    private Integer status;
}
