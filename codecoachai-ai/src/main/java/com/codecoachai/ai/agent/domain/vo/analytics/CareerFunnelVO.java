package com.codecoachai.ai.agent.domain.vo.analytics;

import lombok.Data;

@Data
public class CareerFunnelVO {

    private Integer latestReadinessScore;
    private Long agentTaskDoneCount = 0L;
    private Long agentTaskCount = 0L;
    private Double agentTaskCompletionRate = 0D;
    private Long applicationCount = 0L;
    private Long followedUpApplicationCount = 0L;
    private Long interviewApplicationCount = 0L;
    private Long offerApplicationCount = 0L;
    private Long rejectedOrClosedApplicationCount = 0L;
    private Double interviewRate = 0D;
    private Double offerRate = 0D;
}
