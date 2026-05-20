package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.dto.ResumeJobMatchCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeJobMatchQueryDTO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportDetailVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportListVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.resume.service.ResumeJobMatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/resume-job-match")
public class ResumeJobMatchController {

    private final ResumeJobMatchService resumeJobMatchService;

    @PostMapping("/reports")
    public Result<ResumeJobMatchSubmitVO> createReport(@Valid @RequestBody ResumeJobMatchCreateDTO dto) {
        return Result.success(resumeJobMatchService.createReport(dto));
    }

    @GetMapping("/reports")
    public Result<PageResult<ResumeJobMatchReportListVO>> listReports(@ModelAttribute ResumeJobMatchQueryDTO query) {
        return Result.success(resumeJobMatchService.listReports(query));
    }

    @GetMapping("/reports/{id}")
    public Result<ResumeJobMatchReportDetailVO> getReport(@PathVariable Long id) {
        return Result.success(resumeJobMatchService.getReport(id));
    }

    @PostMapping("/reports/{id}/regenerate")
    public Result<ResumeJobMatchSubmitVO> regenerate(@PathVariable Long id) {
        return Result.success(resumeJobMatchService.regenerate(id));
    }

    @GetMapping("/latest")
    public Result<ResumeJobMatchReportDetailVO> latest(@RequestParam Long resumeId, @RequestParam Long targetJobId) {
        return Result.success(resumeJobMatchService.getLatest(resumeId, targetJobId));
    }
}
