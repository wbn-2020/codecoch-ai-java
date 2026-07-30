package com.codecoachai.ai.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class GenerateEvidenceReuseMaterialDraftDTO {
    private Long userId;
    private Long campaignId;
    private Long applicationId;
    private Long usageId;
    private LocalDateTime dataCutoffAt;
}
