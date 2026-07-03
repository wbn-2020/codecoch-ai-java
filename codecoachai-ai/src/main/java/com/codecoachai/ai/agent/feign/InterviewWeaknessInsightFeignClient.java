package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.domain.vo.analytics.InterviewWeaknessInsightVO;
import com.codecoachai.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-interview", contextId = "interviewWeaknessInsightFeignClient")
public interface InterviewWeaknessInsightFeignClient {

    @GetMapping("/inner/interviews/users/{userId}/weakness-summary")
    Result<InterviewWeaknessInsightVO> weaknessSummary(@PathVariable("userId") Long userId,
                                                       @RequestParam("days") Integer days);
}
