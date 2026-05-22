package com.codecoachai.ai.agent.domain.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class AgentReviewGenerateDTO {
    private Long targetJobId;
    private LocalDate date;
}
