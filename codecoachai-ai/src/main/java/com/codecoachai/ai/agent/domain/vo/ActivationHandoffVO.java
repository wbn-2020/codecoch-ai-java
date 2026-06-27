package com.codecoachai.ai.agent.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ActivationHandoffVO {

    private String code;
    private String stage;
    private Boolean firstOccurrence;
    private Long runId;
    private Long taskId;
    private Long targetJobId;
    private LocalDate planDate;
    private LocalDateTime occurredAt;
    private String requestId;
}
