package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class InterviewComparisonVO {

    private Long id;
    private Boolean comparable;
    private Long targetJobId;
    private String rubricVersion;
    private List<Long> reportIds;
    private Integer firstTotalScore;
    private Integer latestTotalScore;
    private Integer totalScoreDelta;
    private List<InterviewComparisonReasonVO> unavailableReasons;
    private List<InterviewComparisonReasonVO> warnings;
    private List<InterviewComparisonRoundVO> rounds;
    private List<InterviewDimensionComparisonVO> dimensions;
    private Boolean idempotentReplay;
    private LocalDateTime createdAt;
}
