package com.codecoachai.question.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class QuestionDuplicateCheckDTO {

    private Long questionId;
    private List<Long> questionIds;
}
