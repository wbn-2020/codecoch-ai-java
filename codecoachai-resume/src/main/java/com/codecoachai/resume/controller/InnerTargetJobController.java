package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.dto.JobDescriptionParseDTO;
import com.codecoachai.resume.domain.vo.JobDescriptionAnalysisVO;
import com.codecoachai.resume.domain.vo.TargetJobVO;
import com.codecoachai.resume.service.TargetJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/job-targets")
public class InnerTargetJobController {

    private final TargetJobService targetJobService;

    @GetMapping("/users/{userId}/current")
    public Result<TargetJobVO> current(@PathVariable Long userId) {
        return Result.success(targetJobService.getCurrentForUser(userId));
    }

    @GetMapping("/users/{userId}/{id}")
    public Result<TargetJobVO> detail(@PathVariable Long userId, @PathVariable Long id) {
        return Result.success(targetJobService.getTargetJobForUser(id, userId));
    }

    @GetMapping("/users/{userId}/{id}/analysis")
    public Result<JobDescriptionAnalysisVO> analysis(@PathVariable Long userId, @PathVariable Long id) {
        return Result.success(targetJobService.getAnalysisForUser(id, userId));
    }

    @PostMapping("/users/{userId}/{id}/parse")
    public Result<JobDescriptionAnalysisVO> parse(@PathVariable Long userId,
                                                  @PathVariable Long id,
                                                  @RequestBody(required = false) JobDescriptionParseDTO dto) {
        return Result.success(targetJobService.executeJobDescriptionParseForUser(id, userId, dto));
    }
}
