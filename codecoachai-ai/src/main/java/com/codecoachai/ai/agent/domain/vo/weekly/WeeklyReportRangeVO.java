package com.codecoachai.ai.agent.domain.vo.weekly;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WeeklyReportRangeVO {

    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private LocalDateTime rangeStartUtc;
    private LocalDateTime rangeEndUtc;
    private LocalDateTime sourceCutoffAt;
    private String timezone;
    private String windowStatus;
}
