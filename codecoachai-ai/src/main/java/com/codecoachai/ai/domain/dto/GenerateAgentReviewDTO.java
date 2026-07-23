package com.codecoachai.ai.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class GenerateAgentReviewDTO {

    private Long userId;
    private Long targetJobId;
    private String reviewDate;
    private Integer taskCount;
    private Integer doneCount;
    private Integer skippedCount;
    private Integer todoCount;
    private BigDecimal completionRate;
    private BigDecimal agentSuccessRate;
    private Integer readinessScore;
    private List<TaskBrief> tasks;
    private List<String> topSkills;

    @Data
    public static class TaskBrief {

        private String title;
        private String status;
        private String skill;
    }
}
