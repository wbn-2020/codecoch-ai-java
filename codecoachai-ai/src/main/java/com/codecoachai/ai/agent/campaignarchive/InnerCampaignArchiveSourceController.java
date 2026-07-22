package com.codecoachai.ai.agent.campaignarchive;

import com.codecoachai.common.core.domain.Result;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/agent/campaign-archive-sources")
public class InnerCampaignArchiveSourceController {

    private final InnerCampaignArchiveSourceService service;

    @GetMapping("/users/{userId}/campaigns/{campaignId}")
    public Result<InnerCampaignArchiveSourceVO> get(
            @PathVariable Long userId,
            @PathVariable Long campaignId,
            @RequestParam("dataCutoffAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataCutoffAt) {
        return Result.success(service.get(userId, campaignId, dataCutoffAt));
    }
}
