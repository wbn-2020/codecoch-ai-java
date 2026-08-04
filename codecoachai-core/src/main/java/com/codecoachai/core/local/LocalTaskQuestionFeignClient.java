package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.controller.InnerQuestionController;
import com.codecoachai.question.controller.InnerQuestionRecommendationController;
import com.codecoachai.task.feign.QuestionFeignClient;
import com.codecoachai.task.feign.dto.ExecuteQuestionRecommendationDTO;
import com.codecoachai.task.feign.dto.SaveQuestionDraftsDTO;
import com.codecoachai.task.feign.vo.QuestionRecommendationGenerateVO;
import com.codecoachai.task.feign.vo.SaveQuestionDraftsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalTaskQuestionFeignClient implements QuestionFeignClient {

    private final InnerQuestionController innerQuestionController;
    private final InnerQuestionRecommendationController innerQuestionRecommendationController;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<SaveQuestionDraftsVO> saveDrafts(SaveQuestionDraftsDTO dto) {
        return resultMapper.value(
                innerQuestionController.saveDrafts(
                        resultMapper.convert(dto, InnerQuestionController.SaveQuestionDraftsDTO.class)),
                SaveQuestionDraftsVO.class);
    }

    @Override
    public Result<QuestionRecommendationGenerateVO> executeRecommendation(
            Long batchId, ExecuteQuestionRecommendationDTO dto) {
        return resultMapper.value(
                innerQuestionRecommendationController.execute(
                        batchId,
                        resultMapper.convert(
                                dto,
                                com.codecoachai.question.domain.dto.ExecuteQuestionRecommendationDTO.class)),
                QuestionRecommendationGenerateVO.class);
    }
}
