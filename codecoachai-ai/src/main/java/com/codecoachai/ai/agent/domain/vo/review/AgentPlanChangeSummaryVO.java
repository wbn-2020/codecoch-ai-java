package com.codecoachai.ai.agent.domain.vo.review;

import lombok.Data;

@Data
public class AgentPlanChangeSummaryVO {

    private Integer addCount = 0;
    private Integer removeCount = 0;
    private Integer rescheduleCount = 0;
    private Integer priorityChangeCount = 0;
    private Integer beforeTaskCount = 0;
    private Integer afterTaskCount = 0;
    private Integer beforeMinutes = 0;
    private Integer afterMinutes = 0;
}
