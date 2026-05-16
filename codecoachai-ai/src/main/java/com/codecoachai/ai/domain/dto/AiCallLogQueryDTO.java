package com.codecoachai.ai.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiCallLogQueryDTO {

    private Long userId;
    private String scene;
    private String modelName;
    private Long promptTemplateId;
    private Long promptTemplateVersionId;
    private String promptVersion;
    private String traceId;
    private String requestId;
    private Integer success;
    private Integer status;
    private String businessId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
