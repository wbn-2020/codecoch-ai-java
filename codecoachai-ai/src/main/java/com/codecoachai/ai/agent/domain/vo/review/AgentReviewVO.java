package com.codecoachai.ai.agent.domain.vo.review;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentReviewVO {
    private Long id;
    private Long userId;
    private Long targetJobId;
    private LocalDate reviewDate;
    private String summary;
    private Integer doneCount;
    private Integer skippedCount;
    private Integer todoCount;
    private BigDecimal completionRate;
    private Integer readinessScore;
    private List<String> nextActions = new ArrayList<>();
    private Long agentRunId;
    private Long aiCallLogId;
    private LocalDateTime createdAt;
}
