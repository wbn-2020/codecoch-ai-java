package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.domain.dto.BatchQuestionDuplicateIgnoreDTO;
import com.codecoachai.question.domain.dto.BatchQuestionDuplicateMergeDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateCheckDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvalCaseQueryDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvalCaseSaveDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvalRunRequestDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateEvaluationDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateIgnoreDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateMergeDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateReviewQueryDTO;
import com.codecoachai.question.domain.dto.QuestionRelationCreateDTO;
import com.codecoachai.question.domain.vo.QuestionDuplicateCheckResultVO;
import com.codecoachai.question.domain.vo.BatchQuestionDuplicateResultVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateEvalCaseVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateEvalRunVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateEvaluationVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewDetailVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateFeedbackStatsVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewListVO;
import com.codecoachai.question.domain.vo.QuestionRelationVO;
import com.codecoachai.question.service.QuestionDuplicateEvaluationService;
import com.codecoachai.question.service.QuestionDuplicateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminQuestionDuplicateReviewController {

    private final QuestionDuplicateService duplicateService;
    private final QuestionDuplicateEvaluationService duplicateEvaluationService;

    @PostMapping("/admin/questions/check-duplicate")
    public Result<QuestionDuplicateCheckResultVO> checkDuplicate(@RequestBody QuestionDuplicateCheckDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.checkDuplicate(dto));
    }

    @GetMapping("/admin/question-duplicate-reviews")
    public Result<PageResult<QuestionDuplicateReviewListVO>> pageReviews(QuestionDuplicateReviewQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.pageReviews(query));
    }

    @GetMapping("/admin/question-relations")
    public Result<PageResult<QuestionDuplicateReviewListVO>> pageRelations(QuestionDuplicateReviewQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.pageReviews(query));
    }

    @GetMapping("/admin/question-duplicate-reviews/feedback-stats")
    public Result<QuestionDuplicateFeedbackStatsVO> feedbackStats() {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.feedbackStats());
    }

    @PostMapping("/admin/question-duplicate-reviews/evaluate")
    public Result<QuestionDuplicateEvaluationVO> evaluate(@RequestBody QuestionDuplicateEvaluationDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.evaluate(dto));
    }

    @GetMapping({"/admin/question-duplicate-eval/cases", "/admin/question-duplicate-reviews/eval/cases"})
    public Result<PageResult<QuestionDuplicateEvalCaseVO>> pageEvalCases(QuestionDuplicateEvalCaseQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateEvaluationService.pageCases(query));
    }

    @PostMapping({"/admin/question-duplicate-eval/cases", "/admin/question-duplicate-reviews/eval/cases"})
    public Result<QuestionDuplicateEvalCaseVO> saveEvalCase(@RequestBody QuestionDuplicateEvalCaseSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateEvaluationService.saveCase(dto));
    }

    @DeleteMapping({"/admin/question-duplicate-eval/cases/{id}", "/admin/question-duplicate-reviews/eval/cases/{id}"})
    public Result<Void> deleteEvalCase(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        duplicateEvaluationService.deleteCase(id);
        return Result.success();
    }

    @PostMapping({"/admin/question-duplicate-eval/runs", "/admin/question-duplicate-reviews/eval/runs"})
    public Result<QuestionDuplicateEvalRunVO> runEval(@RequestBody(required = false) QuestionDuplicateEvalRunRequestDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateEvaluationService.run(dto));
    }

    @GetMapping({"/admin/question-duplicate-eval/runs", "/admin/question-duplicate-reviews/eval/runs"})
    public Result<PageResult<QuestionDuplicateEvalRunVO>> pageEvalRuns(@RequestParam(required = false) Long pageNo,
                                                                       @RequestParam(required = false) Long pageSize) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateEvaluationService.pageRuns(pageNo, pageSize));
    }

    @GetMapping({"/admin/question-duplicate-eval/runs/{id}", "/admin/question-duplicate-reviews/eval/runs/{id}"})
    public Result<QuestionDuplicateEvalRunVO> getEvalRun(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateEvaluationService.getRun(id));
    }

    @GetMapping("/admin/question-duplicate-reviews/{id}")
    public Result<QuestionDuplicateReviewDetailVO> getReview(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.getReview(id));
    }

    @PostMapping("/admin/question-duplicate-reviews/{id}/merge")
    public Result<QuestionDuplicateReviewDetailVO> merge(@PathVariable Long id,
                                                         @RequestBody(required = false) QuestionDuplicateMergeDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.merge(id, dto));
    }

    @PostMapping("/admin/question-duplicate-reviews/{id}/ignore")
    public Result<QuestionDuplicateReviewDetailVO> ignore(@PathVariable Long id,
                                                          @RequestBody(required = false) QuestionDuplicateIgnoreDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.ignore(id, dto));
    }

    @PostMapping("/admin/question-duplicate-reviews/batch-merge")
    public Result<BatchQuestionDuplicateResultVO> batchMerge(@RequestBody BatchQuestionDuplicateMergeDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.batchMerge(dto));
    }

    @PostMapping("/admin/question-duplicate-reviews/batch-ignore")
    public Result<BatchQuestionDuplicateResultVO> batchIgnore(@RequestBody BatchQuestionDuplicateIgnoreDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.batchIgnore(dto));
    }

    @GetMapping("/admin/questions/{id}/relations")
    public Result<List<QuestionRelationVO>> listRelations(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.listRelations(id));
    }

    @PostMapping("/admin/questions/{id}/relations")
    public Result<QuestionRelationVO> createRelation(@PathVariable Long id,
                                                     @Valid @RequestBody QuestionRelationCreateDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(duplicateService.createRelation(id, dto));
    }

    @DeleteMapping("/admin/questions/{id}/relations/{relationId}")
    public Result<Void> deleteRelation(@PathVariable Long id, @PathVariable Long relationId) {
        SecurityAssert.requireAdmin();
        duplicateService.deleteRelation(id, relationId);
        return Result.success();
    }
}
