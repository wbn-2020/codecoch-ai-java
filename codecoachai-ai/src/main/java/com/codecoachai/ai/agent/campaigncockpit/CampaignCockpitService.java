package com.codecoachai.ai.agent.campaigncockpit;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CockpitView;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ScenarioPreview;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ScenarioRequest;
public interface CampaignCockpitService {

    CockpitView get(Long userId, Long campaignId);

    ScenarioPreview previewScenario(Long userId, Long campaignId, ScenarioRequest request);
}
