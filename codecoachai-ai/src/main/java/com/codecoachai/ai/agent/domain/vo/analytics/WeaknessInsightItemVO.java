package com.codecoachai.ai.agent.domain.vo.analytics;

import lombok.Data;

@Data
public class WeaknessInsightItemVO {

    private String name;
    private String category;
    private Long count = 0L;
    private String evidence;
    private String recommendedActionType;
    private String actionPath;
}
