package com.codecoachai.file.domain.dto;

import lombok.Data;

@Data
public class AdminFileQueryDTO {

    private Long pageNo = 1L;
    private Long pageSize = 10L;
    private Long userId;
    private String bizType;
    private String status;
    private String parseStatus;
}
