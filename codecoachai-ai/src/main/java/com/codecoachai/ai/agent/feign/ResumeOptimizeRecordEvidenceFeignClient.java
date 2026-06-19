package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.feign.vo.ResumeOptimizeRecordEvidenceVO;
import com.codecoachai.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "codecoachai-resume", contextId = "resumeOptimizeRecordEvidenceFeignClient")
public interface ResumeOptimizeRecordEvidenceFeignClient {

    @GetMapping("/inner/resumes/users/{userId}/optimize-records/{recordId}/agent-evidence")
    Result<ResumeOptimizeRecordEvidenceVO> getOptimizeRecordEvidence(@PathVariable("userId") Long userId,
                                                                     @PathVariable("recordId") Long recordId);
}
