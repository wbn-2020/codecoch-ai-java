package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.domain.dto.ExecuteQuestionRecommendationDTO;
import com.codecoachai.question.domain.vo.QuestionRecommendationGenerateVO;
import com.codecoachai.question.service.QuestionRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/questions/recommendations")
public class InnerQuestionRecommendationController {

    private final QuestionRecommendationService questionRecommendationService;

    @PostMapping("/{batchId}/execute")
    public Result<QuestionRecommendationGenerateVO> execute(@PathVariable Long batchId,
                                                            @RequestBody ExecuteQuestionRecommendationDTO dto) {
        if (dto == null || dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息缺失");
        }
        return Result.success(questionRecommendationService.executeBatch(batchId, dto.getUserId()));
    }
}
