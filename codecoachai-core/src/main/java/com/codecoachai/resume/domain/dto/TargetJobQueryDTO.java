package com.codecoachai.resume.domain.dto;

import lombok.Data;

@Data
public class TargetJobQueryDTO {

    private String keyword;
    private Integer status;
    private Boolean current;
}
