package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.GenerateInterviewPreparationDTO;
import com.codecoachai.ai.domain.vo.GenerateInterviewPreparationVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/interview-preparations")
public class InnerInterviewPreparationAiController {

    private final AiService aiService;

    @PostMapping("/generate")
    public Result<GenerateInterviewPreparationVO> generate(
            @RequestBody GenerateInterviewPreparationDTO dto) {
        return Result.success(aiService.generateInterviewPreparation(dto));
    }
}
