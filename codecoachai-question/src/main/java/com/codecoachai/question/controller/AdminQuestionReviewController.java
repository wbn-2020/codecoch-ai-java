package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.domain.dto.AiQuestionGenerateRequestDTO;
import com.codecoachai.question.domain.dto.QuestionReviewApproveDTO;
import com.codecoachai.question.domain.dto.QuestionReviewQueryDTO;
import com.codecoachai.question.domain.dto.QuestionReviewRejectDTO;
import com.codecoachai.question.domain.vo.AiQuestionGenerateResultVO;
import com.codecoachai.question.domain.vo.QuestionReviewDetailVO;
import com.codecoachai.question.domain.vo.QuestionReviewListVO;
import com.codecoachai.question.service.QuestionReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Admin AI Question Review", description = "Admin APIs for AI-generated question drafts and review. /inner/** AI APIs are internal only and must not be called by frontend clients.")
public class AdminQuestionReviewController {

    private final QuestionReviewService questionReviewService;

    @PostMapping("/admin/ai/questions/generate")
    @Operation(summary = "Generate AI question drafts", description = "Admin endpoint. Generated questions enter question_review as PENDING drafts and do not directly enter the formal question bank.")
    public Result<AiQuestionGenerateResultVO> generate(@Valid @RequestBody AiQuestionGenerateRequestDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(questionReviewService.generate(dto));
    }

    @GetMapping("/admin/question-reviews")
    @Operation(summary = "Page AI question review drafts", description = "Admin endpoint for the question_review pool.")
    public Result<PageResult<QuestionReviewListVO>> pageReviews(QuestionReviewQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(questionReviewService.pageReviews(query));
    }

    @GetMapping("/admin/question-reviews/{id}")
    @Operation(summary = "Get AI question review detail", description = "Admin endpoint for one question_review draft.")
    public Result<QuestionReviewDetailVO> getReview(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(questionReviewService.getReview(id));
    }

    @PostMapping("/admin/question-reviews/{id}/approve")
    @Operation(summary = "Approve AI question draft", description = "Admin endpoint. Approval writes the draft into the formal question table.")
    public Result<QuestionReviewDetailVO> approve(@PathVariable Long id,
                                                  @RequestBody(required = false) QuestionReviewApproveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(questionReviewService.approve(id, dto));
    }

    @PostMapping("/admin/question-reviews/{id}/reject")
    @Operation(summary = "Reject AI question draft", description = "Admin endpoint. Rejected drafts stay out of the formal question table.")
    public Result<QuestionReviewDetailVO> reject(@PathVariable Long id,
                                                 @Valid @RequestBody QuestionReviewRejectDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(questionReviewService.reject(id, dto));
    }
}
