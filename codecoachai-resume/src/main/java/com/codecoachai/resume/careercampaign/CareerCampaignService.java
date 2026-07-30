package com.codecoachai.resume.careercampaign;

import com.codecoachai.resume.careercampaign.CareerCampaignModels.CampaignView;
import com.codecoachai.resume.careercampaign.CareerCampaignModels.SaveRequest;
import java.util.List;

public interface CareerCampaignService {

    List<CampaignView> list();

    CampaignView create(SaveRequest request);

    CampaignView get(Long campaignId);

    CampaignView update(Long campaignId, SaveRequest request);

    CampaignView activate(Long campaignId);

    CampaignView complete(Long campaignId, boolean retainOpenApplications);

    CampaignView completeForUser(Long userId, Long campaignId, boolean retainOpenApplications,
                                 String idempotencyKey, String note);

    CampaignView archive(Long campaignId);

    CampaignView addApplication(Long campaignId, Long applicationId);

    void removeApplication(Long campaignId, Long applicationId);

    default CampaignView activate(Long campaignId, Integer expectedLockVersion,
                                  String idempotencyKey, String note) {
        return activate(campaignId);
    }

    default CampaignView complete(Long campaignId, boolean retainOpenApplications,
                                  Integer expectedLockVersion, String idempotencyKey, String note) {
        return complete(campaignId, retainOpenApplications);
    }

    default CampaignView archive(Long campaignId, Integer expectedLockVersion,
                                 String idempotencyKey, String note) {
        return archive(campaignId);
    }

    default CampaignView addApplication(Long campaignId, Long applicationId, String idempotencyKey) {
        return addApplication(campaignId, applicationId);
    }

    default void removeApplication(Long campaignId, Long applicationId, String idempotencyKey) {
        removeApplication(campaignId, applicationId);
    }
}
