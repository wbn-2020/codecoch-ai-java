package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.service.ResumeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/resumes")
public class InnerResumeController {

    private final ResumeService resumeService;

    @GetMapping("/{id}")
    public Result<InnerResumeDetailVO> getResume(@PathVariable Long id) {
        return Result.success(resumeService.getInnerResume(id));
    }

    @GetMapping("/{id}/projects")
    public Result<List<ResumeProjectVO>> getProjects(@PathVariable Long id) {
        return Result.success(resumeService.getInnerResume(id).getProjects());
    }

    @GetMapping("/default")
    public Result<InnerResumeDetailVO> getDefaultResume() {
        return Result.success(resumeService.getDefaultInnerResume());
    }
}
