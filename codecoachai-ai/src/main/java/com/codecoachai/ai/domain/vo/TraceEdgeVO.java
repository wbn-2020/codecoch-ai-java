package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class TraceEdgeVO {

    private String id;
    private String fromNodeId;
    private String toNodeId;
    private String edgeType;
    private String label;
    private String confidence;
    private String reason;
}
