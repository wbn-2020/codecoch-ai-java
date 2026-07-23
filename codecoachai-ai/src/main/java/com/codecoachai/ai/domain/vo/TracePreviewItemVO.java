package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class TracePreviewItemVO {

    private String label;
    private String value;
    private String hash;
    private Integer length;
    private String displayPolicy;
}
