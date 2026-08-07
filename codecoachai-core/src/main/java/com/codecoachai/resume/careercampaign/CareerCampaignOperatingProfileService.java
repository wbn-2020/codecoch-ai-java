package com.codecoachai.resume.careercampaign;

import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileModels.OperatingProfileView;
import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileModels.SaveRequest;

public interface CareerCampaignOperatingProfileService {

    OperatingProfileView get(Long campaignId);

    OperatingProfileView getForUser(Long userId, Long campaignId);

    OperatingProfileView save(Long campaignId, SaveRequest request);
}
