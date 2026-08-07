package com.codecoachai.question.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class BatchQuestionReviewResultVO {

    private Integer total;
    private Integer successCount;
    private Integer failureCount;
    private List<Long> successIds = new ArrayList<>();
    private List<BatchQuestionReviewFailureVO> failures = new ArrayList<>();
}
