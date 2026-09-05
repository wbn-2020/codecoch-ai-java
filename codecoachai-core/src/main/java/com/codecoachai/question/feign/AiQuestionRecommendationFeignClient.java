package com.codecoachai.question.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.dto.GenerateQuestionRecommendationDTO;
import com.codecoachai.question.feign.vo.GenerateQuestionRecommendationVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-ai", contextId = "aiQuestionRecommendationFeignClient")
public interface AiQuestionRecommendationFeignClient {

    @PostMapping("/inner/ai/question-recommendations/generate")
    Result<GenerateQuestionRecommendationVO> generate(@RequestBody GenerateQuestionRecommendationDTO dto);
}
