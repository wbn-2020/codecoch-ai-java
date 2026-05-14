package com.codecoachai.ai.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiCallLogQueryDTO {

    private Long userId;
    private String scene;
    private String modelName;
    private Integer status;
    private String businessId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
