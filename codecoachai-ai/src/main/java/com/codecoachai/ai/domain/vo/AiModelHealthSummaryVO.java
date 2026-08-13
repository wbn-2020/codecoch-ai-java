package com.codecoachai.ai.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "Read-only AI model health summary based on persisted call history")
public class AiModelHealthSummaryVO {

    private Long modelId;
    private String provider;
    private String modelCode;

    @Schema(description = "HEALTHY, DEGRADED, or UNKNOWN")
    private String healthStatus;

    @Schema(description = "SUCCESS, FAILED, or UNKNOWN")
    private String lastCallStatus;

    private LocalDateTime lastCallAt;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailureAt;

    @Schema(description = "Masked and length-limited failure summary")
    private String lastFailureSummary;
}
