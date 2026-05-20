package com.codecoachai.ai.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class GenerateQuestionRecommendationVO {

    private Long batchId;
    private Long aiCallLogId;
    private List<QuestionRecommendationItemVO> questions;
    private String rawResponse;
}
