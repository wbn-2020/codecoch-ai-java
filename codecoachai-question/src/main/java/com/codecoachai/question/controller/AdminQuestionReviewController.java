package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.question.domain.dto.AiQuestionGenerateRequestDTO;
import com.codecoachai.question.domain.dto.BatchQuestionReviewApproveDTO;
import com.codecoachai.question.domain.dto.BatchQuestionReviewRejectDTO;
import com.codecoachai.question.domain.dto.QuestionReviewApproveDTO;
import com.codecoachai.question.domain.dto.QuestionReviewQueryDTO;
import com.codecoachai.question.domain.dto.QuestionReviewRejectDTO;
import com.codecoachai.question.domain.vo.AiQuestionGenerateResultVO;
import com.codecoachai.question.domain.vo.BatchQuestionReviewResultVO;
import com.codecoachai.question.domain.vo.QuestionReviewDetailVO;
import com.codecoachai.question.domain.vo.QuestionReviewListVO;
import com.codecoachai.question.service.QuestionReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Admin AI Question Review", description = "Admin APIs for AI-generated question drafts and review. /inner/** AI APIs are internal only and must not be called by frontend clients.")
public class AdminQuestionReviewController {

    private static final String PERM_QUESTION_GENERATE = "admin:question:generate";
    private static final String PERM_QUESTION_REVIEW = "admin:question:review";

    private final QuestionReviewService questionReviewService;
    private final AdminPermissionGuard adminPermissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @OperationLog(module = "question", action = "GENERATE_AI_QUESTION", description = "Generate AI question drafts", logArgs = false, logResponse = false)
    @PostMapping("/admin/ai/questions/generate")
    @Operation(summary = "Generate AI question drafts", description = "Admin endpoint. Generated questions enter question_review as PENDING drafts and do not directly enter the formal question bank.")
    public Result<AiQuestionGenerateResultVO> generate(@Valid @RequestBody AiQuestionGenerateRequestDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_GENERATE);
        return runConfirmedOperation("question-ai-generate",
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(questionReviewService.generate(dto)));
    }

    @OperationLog(module = "question", action = "SUBMIT_AI_QUESTION_GENERATE", description = "Submit AI question draft generation task", logArgs = false, logResponse = false)
    @PostMapping("/admin/ai/questions/generate/submit")
    @Operation(summary = "Submit AI question draft generation task", description = "Admin endpoint. The task enters async_task and generated drafts are saved to question_review after completion.")
    public Result<AiQuestionGenerateResultVO> submitGenerate(@Valid @RequestBody AiQuestionGenerateRequestDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_GENERATE);
        return runConfirmedOperation("question-ai-generate-submit",
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(questionReviewService.submitGenerate(dto)));
    }

    @GetMapping("/admin/question-reviews")
    @Operation(summary = "Page AI question review drafts", description = "Admin endpoint for the question_review pool.")
    public Result<PageResult<QuestionReviewListVO>> pageReviews(QuestionReviewQueryDTO query) {
        adminPermissionGuard.require(PERM_QUESTION_REVIEW);
        return Result.success(questionReviewService.pageReviews(query));
    }

    @GetMapping("/admin/question-reviews/{id}")
    @Operation(summary = "Get AI question review detail", description = "Admin endpoint for one question_review draft.")
    public Result<QuestionReviewDetailVO> getReview(@PathVariable Long id) {
        adminPermissionGuard.require(PERM_QUESTION_REVIEW);
        return Result.success(questionReviewService.getReview(id));
    }

    @OperationLog(module = "question", action = "APPROVE_QUESTION_REVIEW", description = "Approve AI question draft", logArgs = false, logResponse = false)
    @PostMapping("/admin/question-reviews/{id}/approve")
    @Operation(summary = "Approve AI question draft", description = "Admin endpoint. Approval writes the draft into the formal question table.")
    public Result<QuestionReviewDetailVO> approve(@PathVariable Long id,
                                                  @RequestBody(required = false) QuestionReviewApproveDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_REVIEW);
        return runConfirmedOperation("question-review-approve:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> Result.success(questionReviewService.approve(id, dto)));
    }

    @OperationLog(module = "question", action = "BATCH_APPROVE_QUESTION_REVIEW", description = "Batch approve AI question drafts", logArgs = false, logResponse = false)
    @PostMapping("/admin/question-reviews/batch-approve")
    @Operation(summary = "Batch approve AI question drafts", description = "Admin endpoint. Each draft reuses the single approve flow; failed items are returned without stopping the batch.")
    public Result<BatchQuestionReviewResultVO> batchApprove(
            @Valid @RequestBody BatchQuestionReviewApproveDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_REVIEW);
        return runConfirmedOperation("question-review-batch-approve",
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(questionReviewService.batchApprove(dto)));
    }

    @OperationLog(module = "question", action = "REJECT_QUESTION_REVIEW", description = "Reject AI question draft", logArgs = false, logResponse = false)
    @PostMapping("/admin/question-reviews/{id}/reject")
    @Operation(summary = "Reject AI question draft", description = "Admin endpoint. Rejected drafts stay out of the formal question table.")
    public Result<QuestionReviewDetailVO> reject(@PathVariable Long id,
                                                 @Valid @RequestBody QuestionReviewRejectDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_REVIEW);
        return runConfirmedOperation("question-review-reject:" + id,
                dto.getConfirm(), dto.getDryRun(), dto.getRejectReason(), dto.getIdempotencyKey(),
                () -> Result.success(questionReviewService.reject(id, dto)));
    }

    @OperationLog(module = "question", action = "CANCEL_QUESTION_REVIEW", description = "Cancel AI question draft", logArgs = false, logResponse = false)
    @PostMapping("/admin/question-reviews/{id}/cancel")
    @Operation(summary = "Cancel AI question draft", description = "Admin endpoint. Cancelled drafts stay out of the formal question table and are kept for audit.")
    public Result<QuestionReviewDetailVO> cancel(@PathVariable Long id,
                                                 @RequestBody(required = false) QuestionReviewRejectDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_REVIEW);
        return runConfirmedOperation("question-review-cancel:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getRejectReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> Result.success(questionReviewService.cancel(id, dto)));
    }

    @OperationLog(module = "question", action = "BATCH_REJECT_QUESTION_REVIEW", description = "Batch reject AI question drafts", logArgs = false, logResponse = false)
    @PostMapping("/admin/question-reviews/batch-reject")
    @Operation(summary = "Batch reject AI question drafts", description = "Admin endpoint. Each draft reuses the single reject flow; failed items are returned without stopping the batch.")
    public Result<BatchQuestionReviewResultVO> batchReject(
            @Valid @RequestBody BatchQuestionReviewRejectDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_REVIEW);
        return runConfirmedOperation("question-review-batch-reject",
                dto.getConfirm(), dto.getDryRun(), dto.getRejectReason(), dto.getIdempotencyKey(),
                () -> Result.success(questionReviewService.batchReject(dto)));
    }

    private <T> Result<T> runConfirmedOperation(String operation, Boolean confirm, Boolean dryRun,
                                                String reason, String idempotencyKey,
                                                Supplier<Result<T>> action) {
        String lockKey = operationConfirmationGuard.requireConfirmed(operation, confirm, dryRun, reason, idempotencyKey);
        try {
            return action.get();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }
    @Data
    public static class AdminOperationConfirmDTO {
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }
}
