package com.codecoachai.ai.agent.domain.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class AdminAgentTaskQueryDTO {

    private Long pageNo = 1L;
    private Long pageSize = 10L;
    private Long userId;
    private Long targetJobId;
    private LocalDate date;
    private String taskType;
    private String status;
    private String priority;
}
