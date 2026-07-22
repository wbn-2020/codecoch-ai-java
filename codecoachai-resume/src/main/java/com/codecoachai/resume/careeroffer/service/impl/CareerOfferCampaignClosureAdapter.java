package com.codecoachai.resume.careeroffer.service.impl;

import com.codecoachai.resume.careercampaign.CareerCampaignService;
import com.codecoachai.resume.careeroffer.service.CareerOfferCampaignClosurePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CareerOfferCampaignClosureAdapter implements CareerOfferCampaignClosurePort {

    private final CareerCampaignService campaignService;

    @Override
    public void close(Long userId, Long campaignId, boolean retainOpenApplications, String idempotencyKey) {
        campaignService.completeForUser(userId, campaignId, retainOpenApplications,
                idempotencyKey, "Offer 决策已关闭求职周期");
    }
}
