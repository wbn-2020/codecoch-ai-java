package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.domain.vo.QuestionRecommendationItemVO;
import com.codecoachai.question.service.QuestionRecommendationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/questions/recommendations")
public class V3QuestionRecommendationController {

    private final QuestionRecommendationService questionRecommendationService;

    @GetMapping("/by-job-target")
    public Result<List<QuestionRecommendationItemVO>> byJobTarget(@RequestParam Long targetJobId,
                                                                  @RequestParam(required = false) Integer limit) {
        return Result.success(questionRecommendationService.recommendByJobTarget(targetJobId, limit));
    }

    @GetMapping("/by-skill")
    public Result<List<QuestionRecommendationItemVO>> bySkill(@RequestParam(required = false) Long skillProfileId,
                                                              @RequestParam(required = false) String skillCode,
                                                              @RequestParam(required = false) String skillName,
                                                              @RequestParam(required = false) Integer limit) {
        return Result.success(questionRecommendationService.recommendBySkill(skillProfileId, skillCode, skillName, limit));
    }
}
