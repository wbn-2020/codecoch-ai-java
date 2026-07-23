package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.domain.context.JobApplicationAgentContextVO;
import com.codecoachai.ai.agent.domain.context.JobExperimentAgentContextVO;
import com.codecoachai.ai.agent.domain.context.JobDescriptionAnalysisContextVO;
import com.codecoachai.ai.agent.domain.context.ProjectEvidenceAgentContextVO;
import com.codecoachai.ai.agent.domain.context.RequirementReadinessAgentContextVO;
import com.codecoachai.ai.agent.domain.context.TargetJobContextVO;
import com.codecoachai.common.core.domain.Result;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-resume", contextId = "resumeAgentContextFeignClient",
        fallbackFactory = ResumeAgentContextFeignClientFallbackFactory.class)
public interface ResumeAgentContextFeignClient {

    @GetMapping("/inner/job-targets/users/{userId}/current")
    Result<TargetJobContextVO> currentTargetJob(@PathVariable("userId") Long userId);

    @GetMapping("/inner/job-targets/users/{userId}/{id}")
    Result<TargetJobContextVO> getTargetJob(@PathVariable("userId") Long userId,
                                            @PathVariable("id") Long id);

    @GetMapping("/inner/job-targets/users/{userId}/{id}/analysis")
    Result<JobDescriptionAnalysisContextVO> getAnalysis(@PathVariable("userId") Long userId,
                                                        @PathVariable("id") Long id);

    @GetMapping("/inner/applications/users/{userId}/agent-context")
    Result<List<JobApplicationAgentContextVO>> listAgentApplications(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "targetJobId", required = false) Long targetJobId);

    @GetMapping("/inner/project-evidence/users/{userId}/agent-context")
    Result<List<ProjectEvidenceAgentContextVO>> listProjectEvidenceAgentContext(@PathVariable("userId") Long userId);

    @GetMapping("/inner/job-requirements/users/{userId}/targets/{targetJobId}/agent-context")
    Result<RequirementReadinessAgentContextVO> requirementReadinessContext(
            @PathVariable("userId") Long userId,
            @PathVariable("targetJobId") Long targetJobId);

    @GetMapping("/inner/job-experiments/users/{userId}/agent-context")
    Result<List<JobExperimentAgentContextVO>> listJobExperimentAgentContext(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "targetJobId", required = false) Long targetJobId);
}
