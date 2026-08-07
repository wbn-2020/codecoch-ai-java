package com.codecoachai.question.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class QuestionDuplicateThresholdSweepDTO {
    private List<Long> caseIds;
    private Boolean onlyEnabled = true;
    private Integer limit;
    private Integer minThreshold = 70;
    private Integer maxThreshold = 95;
    private Integer step = 5;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
