package com.codecoachai.ai.agent.campaignreview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CareerCampaignMemoryCandidateConfirmDTO {

    @NotBlank
    private String idempotencyKey;
    private Boolean confirmed = true;
}
