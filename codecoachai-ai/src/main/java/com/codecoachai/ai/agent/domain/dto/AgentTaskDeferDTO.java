package com.codecoachai.ai.agent.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentTaskDeferDTO {

    private LocalDateTime deferAt;
    private String deferReason;
}
