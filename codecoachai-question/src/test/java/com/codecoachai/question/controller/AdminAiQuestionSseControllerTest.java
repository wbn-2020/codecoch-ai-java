package com.codecoachai.question.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.dto.AiQuestionGenerateRequestDTO;
import com.codecoachai.question.domain.vo.AiQuestionGenerateResultVO;
import com.codecoachai.question.service.QuestionReviewService;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class AdminAiQuestionSseControllerTest {

    @Mock
    private QuestionReviewService questionReviewService;
    @Mock
    private AdminPermissionGuard adminPermissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminAiQuestionSseController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAiQuestionSseController(
                questionReviewService,
                Runnable::run,
                adminPermissionGuard,
                operationConfirmationGuard);
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(1L)
                .username("admin")
                .roles(List.of("ADMIN"))
                .build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void generateRejectsMissingConfirmationBeforeStartingStream() {
        AiQuestionGenerateRequestDTO dto = new AiQuestionGenerateRequestDTO();
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-ai-generate-sse"),
                isNull(),
                isNull(),
                isNull(),
                isNull()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class, () -> controller.generate(dto));

        verify(adminPermissionGuard).require("admin:question:generate");
        verify(questionReviewService, never()).generate(any());
        verify(operationConfirmationGuard, never()).release(any());
    }

    @Test
    void generateRunsWithConfirmedIdempotencyPayload() {
        AiQuestionGenerateRequestDTO dto = confirmedDto("question-sse-generate-1234");
        AiQuestionGenerateResultVO result = new AiQuestionGenerateResultVO();
        result.setBatchId("batch-1");
        result.setGeneratedCount(2);
        result.setReviewIds(List.of(10L, 11L));
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-ai-generate-sse"),
                eq(true),
                eq(false),
                eq("admin ai question generation confirmed"),
                eq("question-sse-generate-1234")))
                .thenReturn("lock-key");
        when(questionReviewService.generate(dto)).thenReturn(result);

        SseEmitter emitter = controller.generate(dto);

        assertNotNull(emitter);
        verify(adminPermissionGuard).require("admin:question:generate");
        verify(questionReviewService).generate(dto);
        verify(operationConfirmationGuard, never()).release("lock-key");
    }

    @Test
    void generateReleasesIdempotencyLockWhenServiceFails() {
        AiQuestionGenerateRequestDTO dto = confirmedDto("question-sse-generate-5678");
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-ai-generate-sse"),
                eq(true),
                eq(false),
                eq("admin ai question generation confirmed"),
                eq("question-sse-generate-5678")))
                .thenReturn("lock-key");
        doThrow(new IllegalStateException("ai failed"))
                .when(questionReviewService).generate(dto);

        SseEmitter emitter = controller.generate(dto);

        assertNotNull(emitter);
        verify(questionReviewService).generate(dto);
        verify(operationConfirmationGuard).release("lock-key");
    }

    @Test
    void generateDegradesWhenSseExecutorRejectsSubmission() {
        AiQuestionGenerateRequestDTO dto = confirmedDto("question-sse-generate-rejected");
        AdminAiQuestionSseController rejectingController = new AdminAiQuestionSseController(
                questionReviewService,
                new RejectingExecutor(),
                adminPermissionGuard,
                operationConfirmationGuard);
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-ai-generate-sse"),
                eq(true),
                eq(false),
                eq("admin ai question generation confirmed"),
                eq("question-sse-generate-rejected")))
                .thenReturn("lock-key");

        SseEmitter emitter = assertDoesNotThrow(() -> rejectingController.generate(dto),
                "SSE queue rejection should be degraded on the emitter instead of escaping to HTTP thread");

        assertNotNull(emitter);
        verify(questionReviewService, never()).generate(any());
        verify(operationConfirmationGuard).release("lock-key");
    }

    private static AiQuestionGenerateRequestDTO confirmedDto(String idempotencyKey) {
        AiQuestionGenerateRequestDTO dto = new AiQuestionGenerateRequestDTO();
        dto.setTargetPosition("Java backend engineer");
        dto.setCount(2);
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("admin ai question generation confirmed");
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private static final class RejectingExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("question sse queue full");
        }
    }
}
