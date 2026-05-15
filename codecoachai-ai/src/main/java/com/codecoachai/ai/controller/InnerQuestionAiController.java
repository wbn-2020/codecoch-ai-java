package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.GenerateQuestionDraftDTO;
import com.codecoachai.ai.domain.vo.GenerateQuestionDraftVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/questions")
public class InnerQuestionAiController {

    private final AiService aiService;

    @PostMapping("/generate")
    public Result<GenerateQuestionDraftVO> generate(@RequestBody GenerateQuestionDraftDTO dto) {
        return Result.success(aiService.generateQuestionDrafts(dto));
    }
}
