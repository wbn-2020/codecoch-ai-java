package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.vo.AiResultFeedbackStatsVO;
import com.codecoachai.ai.service.AiResultFeedbackService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ai/feedback")
public class AdminAiResultFeedbackController {

    private final AiResultFeedbackService feedbackService;

    @GetMapping("/stats")
    public Result<AiResultFeedbackStatsVO> stats(@RequestParam(required = false) Integer days) {
        SecurityAssert.requireAdmin();
        return Result.success(feedbackService.stats(days));
    }
}
