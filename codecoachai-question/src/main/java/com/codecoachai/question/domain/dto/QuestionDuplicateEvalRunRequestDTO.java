package com.codecoachai.question.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class QuestionDuplicateEvalRunRequestDTO {

    private List<Long> caseIds;
    private Boolean onlyEnabled = true;
    private Integer limit;
}
