package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.vo.InnerStudyPlanVO;
import com.codecoachai.interview.domain.vo.StudyPlanAgentEvidenceVO;
import com.codecoachai.interview.domain.vo.StudyPlanGenerateVO;
import com.codecoachai.interview.service.StudyPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/study-plans")
public class InnerStudyPlanController {

    private final StudyPlanService studyPlanService;

    @GetMapping("/{planId}")
    public Result<InnerStudyPlanVO> getPlan(@PathVariable Long planId) {
        return Result.success(studyPlanService.getInnerPlan(planId));
    }

    @GetMapping("/users/{userId}/plans/{planId}/agent-evidence")
    public Result<StudyPlanAgentEvidenceVO> getPlanEvidence(@PathVariable Long userId,
                                                            @PathVariable Long planId) {
        return Result.success(studyPlanService.getPlanEvidence(userId, planId));
    }

    @PostMapping("/{planId}/execute")
    public Result<StudyPlanGenerateVO> execute(@PathVariable Long planId) {
        return Result.success(studyPlanService.executeGeneration(planId));
    }
}
