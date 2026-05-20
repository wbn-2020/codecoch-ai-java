package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromGapDTO;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromMatchReportDTO;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromStudyPlanDTO;
import com.codecoachai.question.domain.dto.QuestionRecommendationQueryDTO;
import com.codecoachai.question.domain.vo.QuestionRecommendationBatchDetailVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationBatchListVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationGenerateVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationItemVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationSourceTypeVO;
import com.codecoachai.question.service.QuestionRecommendationService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/question-recommendations")
public class QuestionRecommendationController {

    private final QuestionRecommendationService questionRecommendationService;

    @PostMapping("/generate-from-gap")
    public Result<QuestionRecommendationGenerateVO> generateFromGap(
            @Valid @RequestBody QuestionRecommendationGenerateFromGapDTO dto) {
        return Result.success(questionRecommendationService.generateFromGap(dto));
    }

    @PostMapping("/generate-from-match-report")
    public Result<QuestionRecommendationGenerateVO> generateFromMatchReport(
            @Valid @RequestBody QuestionRecommendationGenerateFromMatchReportDTO dto) {
        return Result.success(questionRecommendationService.generateFromMatchReport(dto));
    }

    @PostMapping("/generate-from-study-plan")
    public Result<QuestionRecommendationGenerateVO> generateFromStudyPlan(
            @Valid @RequestBody QuestionRecommendationGenerateFromStudyPlanDTO dto) {
        return Result.success(questionRecommendationService.generateFromStudyPlan(dto));
    }

    @GetMapping("/source-types")
    public Result<List<QuestionRecommendationSourceTypeVO>> sourceTypes() {
        return Result.success(questionRecommendationService.sourceTypes());
    }

    @GetMapping("/batches")
    public Result<PageResult<QuestionRecommendationBatchListVO>> batches(
            @ModelAttribute QuestionRecommendationQueryDTO query) {
        return Result.success(questionRecommendationService.listBatches(query));
    }

    @GetMapping("/batches/{batchId}")
    public Result<QuestionRecommendationBatchDetailVO> batchDetail(@PathVariable Long batchId) {
        return Result.success(questionRecommendationService.batchDetail(batchId));
    }

    @GetMapping("/batches/{batchId}/items")
    public Result<List<QuestionRecommendationItemVO>> batchItems(@PathVariable Long batchId) {
        return Result.success(questionRecommendationService.batchItems(batchId));
    }

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
