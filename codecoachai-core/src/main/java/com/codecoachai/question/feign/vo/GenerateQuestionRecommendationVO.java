package com.codecoachai.question.feign.vo;

import java.util.List;
import lombok.Data;

@Data
public class GenerateQuestionRecommendationVO {

    private Long batchId;
    private Long aiCallLogId;
    private List<QuestionRecommendationDraftItemVO> questions;
    private String rawResponse;
}
