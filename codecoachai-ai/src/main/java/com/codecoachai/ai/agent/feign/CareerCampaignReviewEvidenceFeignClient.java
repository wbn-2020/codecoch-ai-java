package com.codecoachai.ai.agent.feign;

import com.codecoachai.common.core.domain.Result;
import java.time.LocalDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-resume", contextId = "careerCampaignReviewEvidenceFeignClient")
public interface CareerCampaignReviewEvidenceFeignClient {

    @GetMapping("/inner/career-campaigns/users/{userId}/campaigns/{campaignId}/review-evidence")
    Result<CareerCampaignReviewEvidenceVO> get(
            @PathVariable("userId") Long userId,
            @PathVariable("campaignId") Long campaignId,
            @RequestParam("dataCutoffAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataCutoffAt);
}
