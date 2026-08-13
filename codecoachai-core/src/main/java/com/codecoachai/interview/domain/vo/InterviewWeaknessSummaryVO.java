package com.codecoachai.interview.domain.vo;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class InterviewWeaknessSummaryVO {

    private Integer rangeDays;
    private Long interviewCount = 0L;
    private Long reportCount = 0L;
    private List<WeaknessInsightItemVO> topWeaknesses = Collections.emptyList();
}
