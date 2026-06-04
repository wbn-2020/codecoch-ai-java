package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.AiResultFeedbackCreateDTO;
import com.codecoachai.ai.domain.vo.AiResultFeedbackVO;
import com.codecoachai.ai.service.AiResultFeedbackService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/feedback")
public class AiResultFeedbackController {

    private final AiResultFeedbackService feedbackService;

    @PostMapping
    public Result<AiResultFeedbackVO> create(@RequestBody AiResultFeedbackCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(feedbackService.create(userId, dto));
    }
}
