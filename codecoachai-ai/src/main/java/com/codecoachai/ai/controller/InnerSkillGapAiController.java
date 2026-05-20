package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.AnalyzeSkillGapDTO;
import com.codecoachai.ai.domain.vo.AnalyzeSkillGapVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/skill-gaps")
public class InnerSkillGapAiController {

    private final AiService aiService;

    @PostMapping("/analyze")
    public Result<AnalyzeSkillGapVO> analyze(@RequestBody AnalyzeSkillGapDTO dto) {
        return Result.success(aiService.analyzeSkillGap(dto));
    }
}
