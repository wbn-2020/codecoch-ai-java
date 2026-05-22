package com.codecoachai.ai.agent.domain.vo.memory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentMemoryVO {
    private Long id;
    private String memoryType;
    private String content;
    private String sourceType;
    private Long sourceId;
    private BigDecimal confidence;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
