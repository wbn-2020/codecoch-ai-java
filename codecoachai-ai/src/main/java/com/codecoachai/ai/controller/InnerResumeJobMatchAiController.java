package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.AnalyzeResumeJobMatchDTO;
import com.codecoachai.ai.domain.vo.AnalyzeResumeJobMatchVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/resume-job-match")
public class InnerResumeJobMatchAiController {

    private final AiService aiService;

    @PostMapping("/analyze")
    public Result<AnalyzeResumeJobMatchVO> analyze(@RequestBody AnalyzeResumeJobMatchDTO dto) {
        return Result.success(aiService.analyzeResumeJobMatch(dto));
    }
}
