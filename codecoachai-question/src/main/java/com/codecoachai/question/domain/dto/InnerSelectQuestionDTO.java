package com.codecoachai.question.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class InnerSelectQuestionDTO {

    private String mode;
    private String stageType;
    private Long categoryId;
    private String difficulty;
    private List<Long> excludeGroupIds;
}
