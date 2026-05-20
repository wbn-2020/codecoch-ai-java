package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.dto.InterviewWeakPointFeedbackDTO;
import com.codecoachai.resume.domain.vo.InnerSkillProfileVO;
import com.codecoachai.resume.service.SkillProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/skill-profiles")
public class InnerSkillProfileController {

    private final SkillProfileService skillProfileService;

    @GetMapping("/{profileId}")
    public Result<InnerSkillProfileVO> getProfile(@PathVariable Long profileId) {
        return Result.success(skillProfileService.getInnerProfile(profileId));
    }

    @GetMapping("/by-match-report/{matchReportId}")
    public Result<InnerSkillProfileVO> getSuccessByMatchReport(@PathVariable Long matchReportId) {
        return Result.success(skillProfileService.getInnerSuccessProfileByMatchReport(matchReportId));
    }

    @PostMapping("/interview-feedback")
    public Result<Void> feedbackInterviewWeakPoints(@RequestBody InterviewWeakPointFeedbackDTO dto) {
        skillProfileService.feedbackInterviewWeakPoints(dto);
        return Result.success();
    }
}
