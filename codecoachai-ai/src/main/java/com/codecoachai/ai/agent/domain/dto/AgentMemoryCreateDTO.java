package com.codecoachai.ai.agent.domain.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AgentMemoryCreateDTO {
    private String memoryType;
    private String content;
    private String sourceType;
    private Long sourceId;
    private BigDecimal confidence;
}
