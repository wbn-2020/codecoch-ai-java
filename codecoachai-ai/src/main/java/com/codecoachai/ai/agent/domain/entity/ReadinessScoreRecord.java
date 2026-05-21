package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("readiness_score_record")
public class ReadinessScoreRecord extends BaseEntity {
    private Long userId;
    private Long targetJobId;
    private LocalDate scoreDate;
    private Integer score;
    private BigDecimal taskCompletionRate;
    private BigDecimal agentSuccessRate;
    private String evidenceJson;
}
