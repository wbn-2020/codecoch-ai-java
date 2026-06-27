package com.codecoachai.ai.agent.domain.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class AdminAgentRunQueryDTO {

    private Long pageNo = 1L;
    private Long pageSize = 10L;
    private Long userId;
    private Long targetJobId;
    private LocalDate date;
    private LocalDate startDate;
    private LocalDate endDate;
    private String agentType;
    private String triggerType;
    private String status;
    private String triggerType;
    private String promptType;
}
