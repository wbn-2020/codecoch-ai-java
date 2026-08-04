package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.feign.QuestionFeignClient;
import com.codecoachai.interview.feign.dto.InnerSelectQuestionDTO;
import com.codecoachai.interview.feign.dto.RecommendQuestionDTO;
import com.codecoachai.interview.feign.vo.InnerQuestionVO;
import com.codecoachai.question.controller.InnerQuestionController;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalInterviewQuestionFeignClient implements QuestionFeignClient {

    private final InnerQuestionController innerQuestionController;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InnerQuestionVO> select(InnerSelectQuestionDTO dto) {
        return resultMapper.value(
                innerQuestionController.select(
                        resultMapper.convert(dto, com.codecoachai.question.domain.dto.InnerSelectQuestionDTO.class)),
                InnerQuestionVO.class);
    }

    @Override
    public Result<InnerQuestionVO> getQuestion(Long id) {
        return resultMapper.value(innerQuestionController.getQuestion(id), InnerQuestionVO.class);
    }

    @Override
    public Result<List<InnerQuestionVO>> recommendForReport(RecommendQuestionDTO dto) {
        return resultMapper.values(
                innerQuestionController.recommend(
                        resultMapper.convert(dto, com.codecoachai.question.domain.dto.RecommendQuestionDTO.class)),
                InnerQuestionVO.class);
    }
}
