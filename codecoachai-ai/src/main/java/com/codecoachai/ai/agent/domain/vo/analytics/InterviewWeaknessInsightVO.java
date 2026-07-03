package com.codecoachai.ai.agent.domain.vo.analytics;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class InterviewWeaknessInsightVO {

    private Integer rangeDays;
    private Long interviewCount = 0L;
    private Long reportCount = 0L;
    private List<WeaknessInsightItemVO> topWeaknesses = new ArrayList<>();
}
