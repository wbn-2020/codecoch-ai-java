package com.codecoachai.system.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminLogSummaryVO {

    private Long totalOperationLogs;
    private Long todayOperationLogs;
    private Long failedOperationLogs;
    private Long todayFailedOperationLogs;
    private LocalDateTime latestOperationAt;

    private Long totalLoginLogs;
    private Long todayLoginLogs;
    private Long failedLoginLogs;
    private Long todayFailedLoginLogs;
    private LocalDateTime latestLoginAt;
}
