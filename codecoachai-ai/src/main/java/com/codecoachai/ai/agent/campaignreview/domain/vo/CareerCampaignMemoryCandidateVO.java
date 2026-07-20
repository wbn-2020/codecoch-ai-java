package com.codecoachai.ai.agent.campaignreview.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerCampaignMemoryCandidateVO {
    private String semanticHash;
    private String title;
    private String content;
    private String sourceRef;
    private String confidenceLevel;
    private Integer validityDays;
    private String status = "PENDING_CONFIRMATION";
    private LocalDateTime expiresAt;
}
