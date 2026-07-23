package com.codecoachai.interview.domain.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class InterviewDimensionComparisonVO {

    private String dimension;
    private BigDecimal firstScore;
    private BigDecimal latestScore;
    private BigDecimal delta;
    private List<InterviewComparisonPointVO> points;
}
