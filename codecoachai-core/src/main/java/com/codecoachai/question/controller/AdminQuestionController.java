package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.question.domain.dto.AdminQuestionSaveDTO;
import com.codecoachai.question.domain.dto.QuestionQueryDTO;
import com.codecoachai.question.domain.dto.UpdateStatusDTO;
import com.codecoachai.question.domain.vo.QuestionDetailVO;
import com.codecoachai.question.domain.vo.QuestionListVO;
import com.codecoachai.question.service.QuestionService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminQuestionController {

    private static final String PERM_QUESTION_LIST = "admin:question:list";
    private static final String PERM_QUESTION_WRITE = "admin:question:write";

    private final QuestionService questionService;
    private final AdminPermissionGuard adminPermissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @GetMapping("/admin/questions")
    public Result<PageResult<QuestionListVO>> pageQuestions(QuestionQueryDTO query) {
        adminPermissionGuard.require(PERM_QUESTION_LIST);
        return Result.success(questionService.pageAdminQuestions(query));
    }

    @GetMapping("/admin/questions/page")
    public Result<PageResult<QuestionListVO>> pageQuestionsAlias(QuestionQueryDTO query) {
        adminPermissionGuard.require(PERM_QUESTION_LIST);
        return Result.success(questionService.pageAdminQuestions(query));
    }

    @GetMapping("/admin/questions/{id}")
    public Result<QuestionDetailVO> getQuestion(@PathVariable Long id) {
        adminPermissionGuard.require(PERM_QUESTION_LIST);
        return Result.success(questionService.getQuestion(id));
    }

    @PostMapping("/admin/questions")
    @OperationLog(module = "question", action = "CREATE_QUESTION", description = "Create question", logArgs = false, logResponse = false)
    public Result<QuestionDetailVO> createQuestion(@Valid @RequestBody AdminQuestionSaveDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_WRITE);
        return runConfirmedOperation("question-create:" + dto.getTitle(),
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(questionService.createQuestion(dto)));
    }

    @PutMapping("/admin/questions/{id}")
    @OperationLog(module = "question", action = "UPDATE_QUESTION", description = "Update question", logArgs = false, logResponse = false)
    public Result<QuestionDetailVO> updateQuestion(@PathVariable Long id,
                                                   @Valid @RequestBody AdminQuestionSaveDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_WRITE);
        return runConfirmedOperation("question-update:" + id,
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> Result.success(questionService.updateQuestion(id, dto)));
    }

    @DeleteMapping("/admin/questions/{id}")
    @OperationLog(module = "question", action = "DELETE_QUESTION", description = "Delete question", logArgs = false, logResponse = false)
    public Result<Void> deleteQuestion(@PathVariable Long id,
                                       @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_WRITE);
        return runConfirmedOperation("question-delete:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    questionService.deleteQuestion(id);
                    return Result.success();
                });
    }

    @PutMapping("/admin/questions/{id}/status")
    @OperationLog(module = "question", action = "UPDATE_QUESTION_STATUS", description = "Update question status", logArgs = false, logResponse = false)
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_WRITE);
        return runConfirmedOperation("question-status:" + id,
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> {
                    questionService.updateStatus(id, dto);
                    return Result.success();
                });
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
