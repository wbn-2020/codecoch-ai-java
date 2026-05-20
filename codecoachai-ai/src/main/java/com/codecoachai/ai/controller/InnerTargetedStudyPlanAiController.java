package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.GenerateTargetedStudyPlanDTO;
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
@RequestMapping("/inner/ai/study-plans")
public class InnerTargetedStudyPlanAiController {

    private final AiService aiService;

    @PostMapping("/generate-from-gap")
    public Result<GenerateLearningPlanVO> generateFromGap(@RequestBody GenerateTargetedStudyPlanDTO dto) {
        return Result.success(aiService.generateTargetedStudyPlan(dto));
    }
}
