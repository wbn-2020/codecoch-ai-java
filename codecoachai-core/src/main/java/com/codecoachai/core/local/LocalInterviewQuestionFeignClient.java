package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.feign.QuestionFeignClient;
import com.codecoachai.interview.feign.dto.InnerSelectQuestionDTO;
import com.codecoachai.interview.feign.dto.RecommendQuestionDTO;
import com.codecoachai.interview.feign.vo.InnerQuestionVO;
import com.codecoachai.question.service.QuestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalInterviewQuestionFeignClient implements QuestionFeignClient {

    private final QuestionService questionService;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InnerQuestionVO> select(InnerSelectQuestionDTO dto) {
        return resultMapper.invoke(() -> resultMapper.value(
                Result.success(questionService.selectForInterview(
                        resultMapper.convertRequiredBody(
                                dto,
                                com.codecoachai.question.domain.dto.InnerSelectQuestionDTO.class))),
                InnerQuestionVO.class));
    }

    @Override
    public Result<InnerQuestionVO> getQuestion(Long id) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(id, "id");
            return resultMapper.value(
                    Result.success(questionService.getInnerQuestion(id)),
                    InnerQuestionVO.class);
        });
    }

    @Override
    public Result<List<InnerQuestionVO>> recommendForReport(RecommendQuestionDTO dto) {
        return resultMapper.invoke(() -> resultMapper.values(
                Result.success(questionService.recommend(
                        resultMapper.convertRequiredBody(
                                dto,
                                com.codecoachai.question.domain.dto.RecommendQuestionDTO.class))),
                InnerQuestionVO.class));
    }
}
