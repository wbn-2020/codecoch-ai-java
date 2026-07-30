package com.codecoachai.ai.agent.campaigncockpit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

public final class CampaignActionDecisionModels {

    private CampaignActionDecisionModels() {
    }

    @Data
    public static class Request {
        @NotBlank
        @Size(max = 255)
        private String semanticKey;
        @NotBlank
        @Size(max = 80)
        private String sourceHash;
        @NotBlank
        @Size(max = 24)
        private String decisionStatus;
        private LocalDateTime snoozedUntil;
        @Size(max = 500)
        private String reason;
        @NotBlank
        @Size(min = 8, max = 128)
        private String idempotencyKey;
    }

    @Data
    public static class View {
        private Long id;
        private Long campaignId;
        private String semanticKey;
        private String sourceHash;
        private String actionType;
        private String decisionStatus;
        private LocalDateTime snoozedUntil;
        private String reason;
        private LocalDateTime decidedAt;
    }
}
