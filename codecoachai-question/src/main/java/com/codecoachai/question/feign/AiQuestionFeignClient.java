package com.codecoachai.question.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.dto.GenerateQuestionDraftDTO;
import com.codecoachai.question.feign.vo.GenerateQuestionDraftVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-ai")
public interface AiQuestionFeignClient {

    @PostMapping("/inner/ai/questions/generate")
    Result<GenerateQuestionDraftVO> generateQuestions(@RequestBody GenerateQuestionDraftDTO dto);
}
