package com.codecoachai.resume.careeroffer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CareerOfferDecisionConfirmDTO {
    @NotNull
    private Long selectedOfferId;
    @NotNull
    private Boolean userConfirmed;
    private Boolean closeCampaign;
    private Boolean retainOpenApplications;
    private Integer expectedLockVersion;
    private Integer applicationLockVersion;
}
