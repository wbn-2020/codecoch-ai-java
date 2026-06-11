package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportDetailVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.resume.service.ResumeJobMatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inner-Resume Job Match")
@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/resume-job-match/reports")
public class InnerResumeJobMatchController {

    private final ResumeJobMatchService resumeJobMatchService;

    @Operation(summary = "Get a SUCCESS resume job match report for downstream evidence")
    @GetMapping("/{id}/success")
    public Result<ResumeJobMatchReportDetailVO> getSuccessReport(@PathVariable Long id) {
        return Result.success(resumeJobMatchService.getInnerSuccessReport(id));
    }

    @Operation(summary = "Execute a queued resume job match report")
    @PostMapping("/{id}/execute")
    public Result<ResumeJobMatchSubmitVO> execute(@PathVariable Long id) {
        return Result.success(resumeJobMatchService.executeReport(id));
    }
}
