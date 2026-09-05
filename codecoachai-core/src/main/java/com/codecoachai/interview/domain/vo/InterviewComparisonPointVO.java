package com.codecoachai.interview.domain.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class InterviewComparisonPointVO {

    private Long reportId;
    private BigDecimal score;
    private BigDecimal deltaFromPrevious;
}
