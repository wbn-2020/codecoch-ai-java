package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class AdminQuestionSaveDTO {

    @NotBlank(message = "title is required")
    private String title;

    private String content;
    private String referenceAnswer;
    private String analysis;
    private Long categoryId;
    private Long groupId;
    private String difficulty;
    private Integer status;
    private List<Long> tagIds;
}
