package com.codecoachai.ai.agent.campaignarchive;

import java.time.LocalDateTime;

public interface InnerCampaignArchiveSourceService {

    InnerCampaignArchiveSourceVO get(Long userId, Long campaignId, LocalDateTime dataCutoffAt);
}
