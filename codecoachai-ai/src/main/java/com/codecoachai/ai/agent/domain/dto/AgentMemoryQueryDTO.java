package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

@Data
public class AgentMemoryQueryDTO {
    private Long pageNo = 1L;
    private Long pageSize = 10L;
    private String memoryType;
    private Integer enabled;
}
