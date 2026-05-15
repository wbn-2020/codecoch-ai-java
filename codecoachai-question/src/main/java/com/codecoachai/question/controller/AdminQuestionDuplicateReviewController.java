package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.domain.dto.QuestionDuplicateCheckDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateIgnoreDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateMergeDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateReviewQueryDTO;
import com.codecoachai.question.domain.dto.QuestionRelationCreateDTO;
import com.codecoachai.question.domain.vo.QuestionDuplicateCheckResultVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewDetailVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewListVO;
import com.codecoachai.question.domain.vo.QuestionRelationVO;
import com.codecoachai.question.service.QuestionDuplicateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminQuestionDuplicateReviewController {

    private final QuestionDuplicateService duplicateService;

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
