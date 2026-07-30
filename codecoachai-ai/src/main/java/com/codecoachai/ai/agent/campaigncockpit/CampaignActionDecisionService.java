package com.codecoachai.ai.agent.campaigncockpit;

import com.codecoachai.ai.agent.campaigncockpit.CampaignActionDecisionModels.Request;
import com.codecoachai.ai.agent.campaigncockpit.CampaignActionDecisionModels.View;
import java.util.List;

public interface CampaignActionDecisionService {

    View decide(Long userId, Long campaignId, Request request);

    List<View> list(Long userId, Long campaignId);
}
