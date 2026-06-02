package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.domain.context.JobDescriptionAnalysisContextVO;
import com.codecoachai.ai.agent.domain.context.TargetJobContextVO;
import com.codecoachai.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "codecoachai-resume", contextId = "resumeAgentContextFeignClient")
public interface ResumeAgentContextFeignClient {

    @GetMapping("/inner/job-targets/users/{userId}/current")
    Result<TargetJobContextVO> currentTargetJob(@PathVariable("userId") Long userId);

    @GetMapping("/inner/job-targets/users/{userId}/{id}")
    Result<TargetJobContextVO> getTargetJob(@PathVariable("userId") Long userId,
                                            @PathVariable("id") Long id);

    @GetMapping("/inner/job-targets/users/{userId}/{id}/analysis")
    Result<JobDescriptionAnalysisContextVO> getAnalysis(@PathVariable("userId") Long userId,
                                                        @PathVariable("id") Long id);
}
