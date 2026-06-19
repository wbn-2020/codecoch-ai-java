package com.codecoachai.question.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.question.domain.dto.AdminQuestionSaveDTO;
import com.codecoachai.question.domain.dto.AiQuestionGenerateRequestDTO;
import com.codecoachai.question.domain.dto.QuestionDuplicateMergeDTO;
import com.codecoachai.question.domain.dto.SaveQuestionCategoryDTO;
import com.codecoachai.question.domain.vo.AiQuestionGenerateResultVO;
import com.codecoachai.question.domain.vo.QuestionCategoryVO;
import com.codecoachai.question.domain.vo.QuestionDetailVO;
import com.codecoachai.question.domain.vo.QuestionDuplicateReviewDetailVO;
import com.codecoachai.question.service.QuestionDuplicateEvaluationService;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.service.QuestionMetadataService;
import com.codecoachai.question.service.QuestionReviewService;
import com.codecoachai.question.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminQuestionGovernanceControllerTest {

    @Mock
    private QuestionService questionService;
    @Mock
    private QuestionMetadataService metadataService;
    @Mock
    private QuestionReviewService questionReviewService;
    @Mock
    private QuestionDuplicateService duplicateService;
    @Mock
    private QuestionDuplicateEvaluationService duplicateEvaluationService;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    @Test
    void createQuestionForwardsDryRunToConfirmationGuard() {
        AdminQuestionController controller =
                new AdminQuestionController(questionService, permissionGuard, operationConfirmationGuard);
        AdminQuestionSaveDTO dto = questionDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-create:HashMap internals"),
                eq(true),
                eq(false),
                eq("create question"),
                eq("question-create-1234")))
                .thenReturn("lock-key");
        when(questionService.createQuestion(dto)).thenReturn(new QuestionDetailVO());

        controller.createQuestion(dto);

        verify(permissionGuard).require("admin:question:write");
        verify(questionService).createQuestion(dto);
    }

    @Test
    void deleteQuestionReleasesIdempotencyLockWhenServiceFails() {
        AdminQuestionController controller =
                new AdminQuestionController(questionService, permissionGuard, operationConfirmationGuard);
        AdminQuestionController.AdminOperationConfirmDTO dto = confirmDto(
                new AdminQuestionController.AdminOperationConfirmDTO(),
                "delete question",
                "question-delete-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-delete:10"),
                eq(true),
                eq(false),
                eq("delete question"),
                eq("question-delete-1234")))
                .thenReturn("lock-key");
        doThrow(new RuntimeException("database unavailable")).when(questionService).deleteQuestion(10L);

        assertThrows(RuntimeException.class, () -> controller.deleteQuestion(10L, dto));

        verify(operationConfirmationGuard).release("lock-key");
    }

    @Test
    void createCategoryForwardsDryRunToConfirmationGuard() {
        AdminQuestionMetadataController controller =
                new AdminQuestionMetadataController(metadataService, permissionGuard, operationConfirmationGuard);
        SaveQuestionCategoryDTO dto = categoryDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-category-create:Java"),
                eq(true),
                eq(false),
                eq("create category"),
                eq("question-category-create-1234")))
                .thenReturn("lock-key");
        when(metadataService.createCategory(dto)).thenReturn(new QuestionCategoryVO());

        controller.createCategory(dto);

        verify(permissionGuard).require("admin:question:category");
        verify(metadataService).createCategory(dto);
    }

    @Test
    void generateDoesNotExecuteServiceWhenGuardRejectsDryRun() {
        AdminQuestionReviewController controller =
                new AdminQuestionReviewController(questionReviewService, permissionGuard, operationConfirmationGuard);
        AiQuestionGenerateRequestDTO dto = generateDto(true);
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-ai-generate"),
                eq(true),
                eq(true),
                eq("generate questions"),
                eq("question-generate-1234")))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "dryRun requests are blocked"));

        assertThrows(BusinessException.class, () -> controller.generate(dto));

        verify(permissionGuard).require("admin:question:generate");
        verify(questionReviewService, never()).generate(dto);
    }

    @Test
    void submitGenerateForwardsDryRunToConfirmationGuard() {
        AdminQuestionReviewController controller =
                new AdminQuestionReviewController(questionReviewService, permissionGuard, operationConfirmationGuard);
        AiQuestionGenerateRequestDTO dto = generateDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-ai-generate-submit"),
                eq(true),
                eq(false),
                eq("generate questions"),
                eq("question-generate-1234")))
                .thenReturn("lock-key");
        when(questionReviewService.submitGenerate(dto)).thenReturn(new AiQuestionGenerateResultVO());

        controller.submitGenerate(dto);

        verify(questionReviewService).submitGenerate(dto);
    }

    @Test
    void duplicateMergeForwardsDryRunToConfirmationGuard() {
        AdminQuestionDuplicateReviewController controller =
                new AdminQuestionDuplicateReviewController(
                        duplicateService,
                        duplicateEvaluationService,
                        permissionGuard,
                        operationConfirmationGuard);
        QuestionDuplicateMergeDTO dto = duplicateMergeDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-dedupe-merge:12"),
                eq(true),
                eq(false),
                eq("merge duplicate"),
                eq("question-dedupe-merge-1234")))
                .thenReturn("lock-key");
        when(duplicateService.merge(12L, dto)).thenReturn(new QuestionDuplicateReviewDetailVO());

        controller.merge(12L, dto);

        verify(permissionGuard).require("admin:question:dedupe");
        verify(duplicateService).merge(12L, dto);
    }

    private static AdminQuestionSaveDTO questionDto(boolean dryRun) {
        AdminQuestionSaveDTO dto = new AdminQuestionSaveDTO();
        dto.setTitle("HashMap internals");
        dto.setContent("Explain HashMap internals.");
        dto.setReferenceAnswer("hash bucket resize");
        dto.setDifficulty("MEDIUM");
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("create question");
        dto.setIdempotencyKey("question-create-1234");
        return dto;
    }

    private static SaveQuestionCategoryDTO categoryDto(boolean dryRun) {
        SaveQuestionCategoryDTO dto = new SaveQuestionCategoryDTO();
        dto.setCategoryName("Java");
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("create category");
        dto.setIdempotencyKey("question-category-create-1234");
        return dto;
    }

    private static AiQuestionGenerateRequestDTO generateDto(boolean dryRun) {
        AiQuestionGenerateRequestDTO dto = new AiQuestionGenerateRequestDTO();
        dto.setTargetPosition("Java backend engineer");
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("generate questions");
        dto.setIdempotencyKey("question-generate-1234");
        return dto;
    }

    private static QuestionDuplicateMergeDTO duplicateMergeDto(boolean dryRun) {
        QuestionDuplicateMergeDTO dto = new QuestionDuplicateMergeDTO();
        dto.setRelationType("SAME_INTENT");
        dto.setReason("merge duplicate");
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setIdempotencyKey("question-dedupe-merge-1234");
        return dto;
    }

    private static <T extends AdminQuestionController.AdminOperationConfirmDTO> T confirmDto(
            T dto, String reason, String idempotencyKey) {
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason(reason);
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }
}
