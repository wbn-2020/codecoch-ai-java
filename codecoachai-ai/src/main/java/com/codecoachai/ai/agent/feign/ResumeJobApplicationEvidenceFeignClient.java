package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.feign.vo.JobApplicationEventEvidenceVO;
import com.codecoachai.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "codecoachai-resume", contextId = "resumeJobApplicationEvidenceFeignClient")
public interface ResumeJobApplicationEvidenceFeignClient {

    @GetMapping("/inner/applications/users/{userId}/events/{eventId}/agent-evidence")
    Result<JobApplicationEventEvidenceVO> getApplicationEventEvidence(@PathVariable("userId") Long userId,
                                                                      @PathVariable("eventId") Long eventId);
}
