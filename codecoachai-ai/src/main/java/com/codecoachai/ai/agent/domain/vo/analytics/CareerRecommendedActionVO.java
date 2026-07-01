package com.codecoachai.ai.agent.domain.vo.analytics;

import lombok.Data;

@Data
public class CareerRecommendedActionVO {

    private String id;
    private String type;
    private String title;
    private String description;
    private String priority;
    private String evidence;
    private String actionLabel;
    private String actionPath;
}
