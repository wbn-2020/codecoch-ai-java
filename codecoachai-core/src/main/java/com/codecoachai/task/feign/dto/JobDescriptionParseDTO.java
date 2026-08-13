package com.codecoachai.task.feign.dto;

import lombok.Data;

@Data
public class JobDescriptionParseDTO {

    private Boolean forceRefresh;
    private String userTargetDirection;
}
