package com.codecoachai.question.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.dto.PracticeReviewDTO;
import com.codecoachai.question.feign.vo.PracticeReviewVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-ai", contextId = "aiPracticeFeignClient")
public interface AiPracticeFeignClient {

    @PostMapping("/inner/ai/practice/review")
    Result<PracticeReviewVO> review(@RequestBody PracticeReviewDTO dto);
}
