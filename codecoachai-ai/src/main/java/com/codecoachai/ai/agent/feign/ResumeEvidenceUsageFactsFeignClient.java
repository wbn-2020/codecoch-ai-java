package com.codecoachai.ai.agent.feign;

import com.codecoachai.common.core.domain.Result;
import java.time.LocalDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-resume", contextId = "resumeEvidenceUsageFactsFeignClient")
public interface ResumeEvidenceUsageFactsFeignClient {

    @GetMapping("/inner/evidence-usages/users/{userId}/facts")
    Result<ResumeEvidenceUsageFactsVO> getFacts(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "campaignId", required = false) Long campaignId,
            @RequestParam(value = "applicationId", required = false) Long applicationId,
            @RequestParam(value = "usageId", required = false) Long usageId,
            @RequestParam(value = "dataCutoffAt", required = false) LocalDateTime dataCutoffAt);
}
