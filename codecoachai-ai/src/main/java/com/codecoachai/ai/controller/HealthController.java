package com.codecoachai.ai.controller;

import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequiredArgsConstructor
@RequestMapping("/health")
public class HealthController {

    private final AiProperties aiProperties;

    @GetMapping
    public Result<String> health() {
        if (!Boolean.TRUE.equals(aiProperties.getMockEnabled())
                && (!StringUtils.hasText(aiProperties.getBaseUrl())
                || !StringUtils.hasText(aiProperties.getApiKey())
                || !StringUtils.hasText(aiProperties.getModel()))) {
            return Result.fail(ErrorCode.SYSTEM_ERROR.getCode(),
                    "codecoachai-ai degraded: real model config requires base-url, api-key and model");
        }
        return Result.success("codecoachai-ai");
    }
}
