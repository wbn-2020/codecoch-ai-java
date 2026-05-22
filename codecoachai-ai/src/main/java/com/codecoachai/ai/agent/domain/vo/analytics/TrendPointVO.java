package com.codecoachai.ai.agent.domain.vo.analytics;

import java.time.LocalDate;
import lombok.Data;

@Data
public class TrendPointVO {

    private LocalDate date;
    private Long generatedCount;
    private Long completedCount;
    private Long skippedCount;
    private Long estimatedMinutes;
    private Long completedMinutes;
    private Long runCount;
    private Long successRunCount;
    private Long failedRunCount;
}
