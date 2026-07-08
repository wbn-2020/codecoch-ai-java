package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class TraceModuleStatusVO {

    private String module;
    private String moduleName;
    private String status;
    private Integer count;
    private String message;
    private String errorMessage;
}
