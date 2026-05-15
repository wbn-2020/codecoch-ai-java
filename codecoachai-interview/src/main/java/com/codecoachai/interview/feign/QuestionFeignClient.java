package com.codecoachai.interview.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.feign.dto.InnerSelectQuestionDTO;
import com.codecoachai.interview.feign.dto.RecommendQuestionDTO;
import com.codecoachai.interview.feign.vo.InnerQuestionVO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-question")
public interface QuestionFeignClient {

    @PostMapping("/inner/questions/select")
    Result<InnerQuestionVO> select(@RequestBody InnerSelectQuestionDTO dto);

    @GetMapping("/inner/questions/{id}")
    Result<InnerQuestionVO> getQuestion(@PathVariable("id") Long id);

    @PostMapping("/inner/questions/recommend-for-report")
    Result<List<InnerQuestionVO>> recommendForReport(@RequestBody RecommendQuestionDTO dto);
}
