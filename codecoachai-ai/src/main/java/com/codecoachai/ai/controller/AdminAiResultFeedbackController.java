package com.codecoachai.ai.controller;

import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.domain.vo.AiResultFeedbackStatsVO;
import com.codecoachai.ai.service.AiResultFeedbackService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ai/feedback")
public class AdminAiResultFeedbackController {

    private static final String PERM_AI_FEEDBACK_STATS = "admin:ai:feedback:stats";

    private final AiResultFeedbackService feedbackService;
    private final V4AdminPermissionGuard permissionGuard;

    @GetMapping("/stats")
    public Result<AiResultFeedbackStatsVO> stats(@RequestParam(required = false) Integer days) {
        permissionGuard.require(PERM_AI_FEEDBACK_STATS);
        return Result.success(feedbackService.stats(days));
    }
}
