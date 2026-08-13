package com.codecoachai.question.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class QuestionDuplicateCheckResultVO {

    private Integer checkedCount;
    private Integer createdCount;
    private List<Long> reviewIds;
}
