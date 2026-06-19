package com.codecoachai.ai.agent.feign;

import com.codecoachai.ai.agent.feign.vo.InterviewReportEvidenceVO;
import com.codecoachai.common.core.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "codecoachai-interview", contextId = "interviewReportEvidenceFeignClient")
public interface InterviewReportEvidenceFeignClient {

    @GetMapping("/inner/interviews/reports/users/{userId}/{reportId}/agent-evidence")
    Result<InterviewReportEvidenceVO> getReportEvidence(@PathVariable("userId") Long userId,
                                                        @PathVariable("reportId") Long reportId);
}
