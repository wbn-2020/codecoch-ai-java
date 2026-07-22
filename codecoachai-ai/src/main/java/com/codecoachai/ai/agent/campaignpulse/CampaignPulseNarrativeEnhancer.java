package com.codecoachai.ai.agent.campaignpulse;

import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Computation;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Narrative;

public interface CampaignPulseNarrativeEnhancer {

    Narrative enhance(Long userId, Long campaignId, Computation computation);
}
