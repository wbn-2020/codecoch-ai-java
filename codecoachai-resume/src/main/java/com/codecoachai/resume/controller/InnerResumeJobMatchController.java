package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportAgentEvidenceVO;
import com.codecoachai.resume.service.ResumeJobMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/resume-job-match")
public class InnerResumeJobMatchController {

    private final ResumeJobMatchService resumeJobMatchService;

    @GetMapping("/reports/users/{userId}/{reportId}/agent-evidence")
    public Result<ResumeJobMatchReportAgentEvidenceVO> getReportEvidence(@PathVariable Long userId,
                                                                         @PathVariable Long reportId) {
        return Result.success(resumeJobMatchService.getReportEvidence(userId, reportId));
    }
}
