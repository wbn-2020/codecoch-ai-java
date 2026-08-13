package com.codecoachai.resume.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.feign.vo.CampaignArchiveAiSourceVO;
import java.time.LocalDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-ai", contextId = "campaignArchiveAiFeignClient")
public interface CampaignArchiveAiFeignClient {

    @GetMapping("/inner/agent/campaign-archive-sources/users/{userId}/campaigns/{campaignId}")
    Result<CampaignArchiveAiSourceVO> getSource(
            @PathVariable("userId") Long userId,
            @PathVariable("campaignId") Long campaignId,
            @RequestParam("dataCutoffAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataCutoffAt);
}
