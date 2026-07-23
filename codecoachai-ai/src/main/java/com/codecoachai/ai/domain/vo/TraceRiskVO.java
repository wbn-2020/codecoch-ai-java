package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class TraceRiskVO {

    private String id;
    private String type;
    private String level;
    private String title;
    private String description;
    private String nodeId;
    private TraceLinkVO link;
}
