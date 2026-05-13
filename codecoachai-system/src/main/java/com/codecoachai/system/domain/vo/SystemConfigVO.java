package com.codecoachai.system.domain.vo;

import lombok.Data;

@Data
public class SystemConfigVO {

    private Long id;
    private String configKey;
    private String configValue;
    private String valueType;
    private String description;
    private Integer status;
}
