package com.codecoachai.task.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.task.feign.dto.ExecuteQuestionRecommendationDTO;
import com.codecoachai.task.feign.dto.SaveQuestionDraftsDTO;
import com.codecoachai.task.feign.vo.QuestionRecommendationGenerateVO;
import com.codecoachai.task.feign.vo.SaveQuestionDraftsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-question", contextId = "taskQuestionFeignClient")
public interface QuestionFeignClient {

    @PostMapping("/inner/questions/reviews/save-drafts")
    Result<SaveQuestionDraftsVO> saveDrafts(@RequestBody SaveQuestionDraftsDTO dto);

    @PostMapping("/inner/questions/recommendations/{batchId}/execute")
    Result<QuestionRecommendationGenerateVO> executeRecommendation(@PathVariable("batchId") Long batchId,
                                                                   @RequestBody ExecuteQuestionRecommendationDTO dto);
}
