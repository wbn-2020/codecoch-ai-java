package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.GenerateLearningPlanDTO;
import com.codecoachai.ai.domain.vo.GenerateLearningPlanVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/learning-plans")
public class InnerLearningPlanAiController {

    private final AiService aiService;

    @PostMapping("/generate")
    public Result<GenerateLearningPlanVO> generate(@RequestBody GenerateLearningPlanDTO dto) {
        return Result.success(aiService.generateLearningPlan(dto));
    }
}
