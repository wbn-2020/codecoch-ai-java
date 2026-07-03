package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.vo.JobExperimentAgentContextVO;
import com.codecoachai.resume.service.JobSearchExperimentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/job-experiments")
public class InnerJobSearchExperimentController {

    private final JobSearchExperimentService jobSearchExperimentService;

    @GetMapping("/users/{userId}/agent-context")
    public Result<List<JobExperimentAgentContextVO>> agentContext(
            @PathVariable Long userId,
            @RequestParam(value = "targetJobId", required = false) Long targetJobId) {
        return Result.success(jobSearchExperimentService.listAgentContextForUser(userId, targetJobId));
    }
}
