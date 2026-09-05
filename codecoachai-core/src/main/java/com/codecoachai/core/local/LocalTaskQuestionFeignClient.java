package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.controller.InnerQuestionController;
import com.codecoachai.question.service.QuestionRecommendationService;
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
    private final QuestionRecommendationService questionRecommendationService;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<SaveQuestionDraftsVO> saveDrafts(SaveQuestionDraftsDTO dto) {
        return resultMapper.invoke(() -> resultMapper.value(
                innerQuestionController.saveDrafts(
                        resultMapper.convertRequiredBody(
                                dto,
                                InnerQuestionController.SaveQuestionDraftsDTO.class)),
                SaveQuestionDraftsVO.class));
    }

    @Override
    public Result<QuestionRecommendationGenerateVO> executeRecommendation(
            Long batchId, ExecuteQuestionRecommendationDTO dto) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(batchId, "batchId");
            com.codecoachai.question.domain.dto.ExecuteQuestionRecommendationDTO request =
                    resultMapper.convertRequiredBody(
                            dto,
                            com.codecoachai.question.domain.dto.ExecuteQuestionRecommendationDTO.class);
            if (request.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息缺失");
            }
            return resultMapper.value(
                    Result.success(questionRecommendationService.executeBatch(batchId, request.getUserId())),
                    QuestionRecommendationGenerateVO.class);
        });
    }
}
