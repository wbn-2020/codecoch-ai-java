package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class TraceRawAccessStatusVO {

    private String state;
    private Boolean rawFieldsAvailable = false;
    private Boolean rawFieldsIncluded = false;
    private String rawAccessPermission;
    private String requiredPermission;
}
