package com.codecoachai.ai.agent.domain.vo.analytics;

import lombok.Data;

@Data
public class AdminAiOverviewVO {

    private Long totalAiCalls;
    private Long successAiCalls;
    private Long failedAiCalls;
    private Double aiSuccessRate;
    private Long avgElapsedMs;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private Long totalTokens;
}
