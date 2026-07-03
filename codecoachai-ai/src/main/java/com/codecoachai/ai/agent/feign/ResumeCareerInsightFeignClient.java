package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.domain.vo.analytics.ApplicationCareerInsightSummaryVO;
import com.codecoachai.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-resume", contextId = "resumeCareerInsightFeignClient")
public interface ResumeCareerInsightFeignClient {

    @GetMapping("/inner/applications/users/{userId}/career-insight-summary")
    Result<ApplicationCareerInsightSummaryVO> careerInsightSummary(@PathVariable("userId") Long userId,
                                                                   @RequestParam("days") Integer days);
}
