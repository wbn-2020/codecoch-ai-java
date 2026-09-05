package com.codecoachai.interview.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class InterviewComparisonRoundVO {

    private Long reportId;
    private Long sessionId;
    private Integer totalScore;
    private LocalDateTime generatedAt;
    private String trustStatus;
    private Boolean sampleInsufficient;
    private String rubricVersion;
    private String normalizationSource;
    private List<InterviewComparisonReasonVO> unavailableReasons;
    private List<InterviewComparisonReasonVO> warnings;
    private Map<String, BigDecimal> rubricScores;
}
