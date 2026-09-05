package com.codecoachai.system.domain.vo;

import lombok.Data;

@Data
public class SystemConfigVO {

    private Long id;
    private String configKey;
    private String configValue;
    private String configValueMasked;
    private String configValueHash;
    private Boolean sensitiveConfig;
    private String rawAccessPermission;
    private String configType;
    private String valueType;
    private String description;
    private Integer status;
}
