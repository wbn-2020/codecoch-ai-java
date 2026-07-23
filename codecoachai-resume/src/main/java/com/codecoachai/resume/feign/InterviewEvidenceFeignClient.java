package com.codecoachai.resume.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.feign.vo.InterviewWeaknessSummaryVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-interview", contextId = "resumeInterviewEvidenceFeignClient")
public interface InterviewEvidenceFeignClient {

    @GetMapping("/inner/interviews/users/{userId}/weakness-summary")
    Result<InterviewWeaknessSummaryVO> weaknessSummary(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer days);
}
