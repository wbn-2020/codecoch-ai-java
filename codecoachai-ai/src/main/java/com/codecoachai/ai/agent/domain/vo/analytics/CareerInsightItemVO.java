package com.codecoachai.ai.agent.domain.vo.analytics;

import lombok.Data;

@Data
public class CareerInsightItemVO {

    private String type;
    private String title;
    private String description;
    private String severity;
    private String evidence;
    private String actionLabel;
    private String actionPath;
}
