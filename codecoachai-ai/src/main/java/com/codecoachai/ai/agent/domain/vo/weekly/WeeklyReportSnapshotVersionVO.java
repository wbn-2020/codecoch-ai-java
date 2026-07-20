package com.codecoachai.ai.agent.domain.vo.weekly;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WeeklyReportSnapshotVersionVO {

    private Long snapshotId;
    private Integer snapshotVersion;
    private String reportStatus;
    private String confidenceLevel;
    private String resultSource;
    private Boolean fallback;
    private LocalDateTime sourceCutoffAt;
    private LocalDateTime generatedAt;
    private Boolean current;
}
