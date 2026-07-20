package com.codecoachai.resume.careeroffer.service;

public interface CareerOfferCampaignClosurePort {

    void close(Long userId, Long campaignId, boolean retainOpenApplications);
}
