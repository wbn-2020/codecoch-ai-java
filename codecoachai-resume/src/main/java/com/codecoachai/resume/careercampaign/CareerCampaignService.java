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

    CampaignView archive(Long campaignId);

    CampaignView addApplication(Long campaignId, Long applicationId);

    void removeApplication(Long campaignId, Long applicationId);
}
