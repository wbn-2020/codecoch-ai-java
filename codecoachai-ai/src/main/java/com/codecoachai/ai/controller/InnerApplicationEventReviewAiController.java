package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.GenerateApplicationEventReviewDTO;
import com.codecoachai.ai.domain.vo.GenerateApplicationEventReviewVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/application-event-reviews")
public class InnerApplicationEventReviewAiController {

    private final AiService aiService;

    @PostMapping("/generate")
    public Result<GenerateApplicationEventReviewVO> generate(
            @RequestBody GenerateApplicationEventReviewDTO dto) {
        return Result.success(aiService.generateApplicationEventReview(dto));
    }
}
