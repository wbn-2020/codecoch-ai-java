package com.codecoachai.ai.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class TraceCockpitQueryDTO {

    private String keyword;
    private String lookupType;
    private String traceId;
    private String requestId;
    private String businessId;
    private String businessType;
    private String bizType;
    private String bizId;
    private Long userId;
    private Long agentRunId;
    private Long asyncTaskId;
    private String messageId;
    private String scene;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
    private Boolean strictTraceOnly = false;
    private Boolean includeSensitive = false;
    private Long pageNo = 1L;
    private Long pageSize = 20L;
}
