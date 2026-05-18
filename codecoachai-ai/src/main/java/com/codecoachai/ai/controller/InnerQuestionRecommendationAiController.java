package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.GenerateQuestionRecommendationDTO;
import com.codecoachai.ai.domain.vo.GenerateQuestionRecommendationVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/question-recommendations")
public class InnerQuestionRecommendationAiController {

    private final AiService aiService;

    @PostMapping("/generate")
    public Result<GenerateQuestionRecommendationVO> generate(@RequestBody GenerateQuestionRecommendationDTO dto) {
        return Result.success(aiService.generateQuestionRecommendations(dto));
    }
}
