package com.codecoachai.resume.careerinterview.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerInterviewRoundEventVO {
    private Long id;
    private Long roundId;
    private String eventType;
    private String payloadJson;
    private LocalDateTime occurredAt;
}
