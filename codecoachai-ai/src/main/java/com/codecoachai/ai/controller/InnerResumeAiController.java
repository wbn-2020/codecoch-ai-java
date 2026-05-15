package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.ParseResumeDTO;
import com.codecoachai.ai.domain.dto.ResumeOptimizeAiRequestDTO;
import com.codecoachai.ai.domain.vo.ParseResumeVO;
import com.codecoachai.ai.domain.vo.ResumeOptimizeAiResponseVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/resume")
public class InnerResumeAiController {

    private final AiService aiService;

    @PostMapping("/parse")
    public Result<ParseResumeVO> parse(@RequestBody ParseResumeDTO dto) {
        return Result.success(aiService.parseResume(dto));
    }

    @PostMapping("/optimize")
    public Result<ResumeOptimizeAiResponseVO> optimize(@RequestBody ResumeOptimizeAiRequestDTO dto) {
        return Result.success(aiService.optimizeResume(dto));
    }
}
