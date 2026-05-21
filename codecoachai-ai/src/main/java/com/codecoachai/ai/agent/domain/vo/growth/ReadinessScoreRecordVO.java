package com.codecoachai.ai.agent.domain.vo.growth;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class ReadinessScoreRecordVO {
    private Long id;
    private Long targetJobId;
    private LocalDate scoreDate;
    private Integer score;
    private BigDecimal taskCompletionRate;
    private BigDecimal agentSuccessRate;
}
