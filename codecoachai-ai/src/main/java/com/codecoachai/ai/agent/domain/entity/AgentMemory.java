package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_memory")
public class AgentMemory extends BaseEntity {
    private Long userId;
    private String memoryType;
    private String content;
    private String sourceType;
    private Long sourceId;
    private BigDecimal confidence;
    private Integer enabled;
}
