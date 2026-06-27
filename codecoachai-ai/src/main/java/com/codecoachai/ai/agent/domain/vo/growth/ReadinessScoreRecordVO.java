package com.codecoachai.ai.agent.domain.vo.growth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ReadinessScoreRecordVO {
    private Long id;
    private Long targetJobId;
    private LocalDate scoreDate;
    private Integer score;
    private BigDecimal taskCompletionRate;
    private BigDecimal agentSuccessRate;
    private String confidenceLevel;
    private Integer evidenceCount;
    private String timeWindow;
    private List<String> dataSourceLabels = new ArrayList<>();
    private String coldStartReason;
    private List<String> nextEvidenceActions = new ArrayList<>();
}
