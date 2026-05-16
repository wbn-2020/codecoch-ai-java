package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.PracticeReviewDTO;
import com.codecoachai.ai.domain.vo.PracticeReviewVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/practice")
public class InnerPracticeAiController {

    private final AiService aiService;

    @PostMapping("/review")
    public Result<PracticeReviewVO> review(@RequestBody PracticeReviewDTO dto) {
        return Result.success(aiService.reviewPractice(dto));
    }
}
