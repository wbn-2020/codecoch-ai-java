package com.codecoachai.question.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionRecommendationBatchDetailVO extends QuestionRecommendationBatchListVO {

    private JsonNode request;
    private JsonNode result;
    private List<QuestionRecommendationItemVO> items;
}
