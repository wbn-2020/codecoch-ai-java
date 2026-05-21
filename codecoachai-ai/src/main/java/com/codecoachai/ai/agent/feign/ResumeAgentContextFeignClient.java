package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.domain.context.JobDescriptionAnalysisContextVO;
import com.codecoachai.ai.agent.domain.context.TargetJobContextVO;
import com.codecoachai.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "codecoachai-resume", contextId = "resumeAgentContextFeignClient")
public interface ResumeAgentContextFeignClient {

    @GetMapping("/job-targets/current")
    Result<TargetJobContextVO> currentTargetJob();

    @GetMapping("/job-targets/{id}")
    Result<TargetJobContextVO> getTargetJob(@PathVariable("id") Long id);

    @GetMapping("/job-targets/{id}/analysis")
    Result<JobDescriptionAnalysisContextVO> getAnalysis(@PathVariable("id") Long id);
}
