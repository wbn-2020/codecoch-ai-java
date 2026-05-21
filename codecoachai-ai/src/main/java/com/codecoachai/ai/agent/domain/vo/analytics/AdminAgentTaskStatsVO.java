package com.codecoachai.ai.agent.domain.vo.analytics;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AdminAgentTaskStatsVO {

    private Long totalAgentTasks;
    private Long doneTaskCount;
    private Long skippedTaskCount;
    private Double taskCompletionRate;
    private List<MetricPointVO> taskTypeDistribution = new ArrayList<>();
    private List<MetricPointVO> priorityDistribution = new ArrayList<>();
}
