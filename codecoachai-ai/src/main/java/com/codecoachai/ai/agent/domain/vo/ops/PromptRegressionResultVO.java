package com.codecoachai.ai.agent.domain.vo.ops;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PromptRegressionResultVO {
    private Long id;
    private Long caseId;
    private Long promptVersionId;
    private String status;
    private String outputJson;
    private Integer score;
    private String errorMessage;
    private LocalDateTime createdAt;
}
