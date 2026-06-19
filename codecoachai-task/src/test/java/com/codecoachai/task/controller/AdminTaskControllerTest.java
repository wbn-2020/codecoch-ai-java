package com.codecoachai.task.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.mq.payload.QuestionGeneratePayload;
import com.codecoachai.common.mq.producer.MqProducer;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.task.domain.dto.AdminTaskActionDTO;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.mapper.MessageDeadLetterMapper;
import com.codecoachai.task.service.AsyncTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminTaskControllerTest {

    @Mock
    private AsyncTaskMapper asyncTaskMapper;
    @Mock
    private MessageDeadLetterMapper deadLetterMapper;
    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private MqProducer mqProducer;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminTaskController controllerWithoutProducer;
    private AdminTaskController controllerWithProducer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        controllerWithoutProducer = new AdminTaskController(
                asyncTaskMapper,
                deadLetterMapper,
                asyncTaskService,
                Optional.empty(),
                objectMapper,
                permissionGuard,
                operationConfirmationGuard);
        controllerWithProducer = new AdminTaskController(
                asyncTaskMapper,
                deadLetterMapper,
                asyncTaskService,
                Optional.of(mqProducer),
                objectMapper,
                permissionGuard,
                operationConfirmationGuard);
    }

    @Test
    void retryTaskRejectsMissingConfirmationBeforeLoadingTask() {
        AdminTaskActionDTO dto = noteOnlyDto();
        when(operationConfirmationGuard.requireConfirmed(
                eq("async-task-retry:7"),
                isNull(),
                isNull(),
                isNull(),
                isNull()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class, () -> controllerWithoutProducer.retryTask(7L, dto));

        verify(permissionGuard).require("admin:task:retry");
        verify(asyncTaskMapper, never()).selectById(any());
        verify(asyncTaskService, never()).prepareManualRetry(any(), any());
        verify(operationConfirmationGuard, never()).release(anyString());
    }

    @Test
    void retryTaskReleasesLockWhenDispatchUnavailableBeforeMqAttempt() {
        AdminTaskActionDTO dto = confirmedDto("admin-task-retry-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("async-task-retry:7"),
                eq(true),
                eq(false),
                eq("confirm async task action"),
                eq("admin-task-retry-1234")))
                .thenReturn("lock-key");
        when(asyncTaskMapper.selectById(7L)).thenReturn(failedQuestionTask());

        assertThrows(BusinessException.class, () -> controllerWithoutProducer.retryTask(7L, dto));

        verify(asyncTaskService).prepareManualRetry(7L, "msg-7");
        verify(asyncTaskService).markManualRetryDispatchFailed(eq(7L), anyString());
        verify(operationConfirmationGuard).release("lock-key");
    }

    @Test
    void recoverDeadLetterKeepsLockWhenMqDispatchWasAttempted() {
        AdminTaskActionDTO dto = confirmedDto("dead-letter-recover-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("dead-letter-recover:12"),
                eq(true),
                eq(false),
                eq("confirm async task action"),
                eq("dead-letter-recover-1234")))
                .thenReturn("lock-key");
        when(deadLetterMapper.selectById(12L)).thenReturn(unhandledQuestionDeadLetter());
        doThrow(new RuntimeException("mq dispatch result unknown"))
                .when(mqProducer)
                .sendSync(anyString(), anyString(), anyString(), any(), any(QuestionGeneratePayload.class));

        assertThrows(RuntimeException.class, () -> controllerWithProducer.recoverDeadLetter(12L, null, dto));

        verify(operationConfirmationGuard, never()).release("lock-key");
        verify(deadLetterMapper, never()).update(any(), any());
    }

    @Test
    void ignoreDeadLetterRequiresConfirmationBeforeUpdating() {
        AdminTaskActionDTO dto = noteOnlyDto();
        when(operationConfirmationGuard.requireConfirmed(
                eq("dead-letter-ignore:12"),
                isNull(),
                isNull(),
                isNull(),
                isNull()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class, () -> controllerWithoutProducer.ignoreDeadLetter(12L, null, dto));

        verify(deadLetterMapper, never()).selectById(any());
        verify(deadLetterMapper, never()).update(any(), any());
        verify(operationConfirmationGuard, never()).release(anyString());
    }

    @Test
    void ignoreDeadLetterRejectsHandledRecordAndReleasesLock() {
        AdminTaskActionDTO dto = confirmedDto("dead-letter-ignore-1234");
        MessageDeadLetter deadLetter = unhandledQuestionDeadLetter();
        deadLetter.setHandleStatus("RECOVERED");
        when(operationConfirmationGuard.requireConfirmed(
                eq("dead-letter-ignore:12"),
                eq(true),
                eq(false),
                eq("confirm async task action"),
                eq("dead-letter-ignore-1234")))
                .thenReturn("lock-key");
        when(deadLetterMapper.selectById(12L)).thenReturn(deadLetter);

        assertThrows(BusinessException.class, () -> controllerWithoutProducer.ignoreDeadLetter(12L, null, dto));

        verify(deadLetterMapper, never()).update(any(), any());
        verify(operationConfirmationGuard).release("lock-key");
    }

    private static AdminTaskActionDTO noteOnlyDto() {
        AdminTaskActionDTO dto = new AdminTaskActionDTO();
        dto.setNote("dependency recovered");
        return dto;
    }

    private static AdminTaskActionDTO confirmedDto(String idempotencyKey) {
        AdminTaskActionDTO dto = noteOnlyDto();
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("confirm async task action");
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private static AsyncTask failedQuestionTask() {
        AsyncTask task = new AsyncTask();
        task.setId(7L);
        task.setMessageId("msg-7");
        task.setBizType("question.generate");
        task.setBizId("batch-1");
        task.setUserId(5L);
        task.setTraceId("trace-7");
        task.setStatus("FAILED");
        task.setRetryCount(1);
        task.setPayload("{\"batchId\":\"batch-1\",\"userId\":5}");
        return task;
    }

    private static MessageDeadLetter unhandledQuestionDeadLetter() {
        MessageDeadLetter deadLetter = new MessageDeadLetter();
        deadLetter.setId(12L);
        deadLetter.setMessageId("msg-12");
        deadLetter.setBizType("question.generate");
        deadLetter.setBizId("batch-1");
        deadLetter.setUserId(5L);
        deadLetter.setTraceId("trace-12");
        deadLetter.setHandleStatus("UNHANDLED");
        deadLetter.setPayload("{\"batchId\":\"batch-1\",\"userId\":5}");
        return deadLetter;
    }
}
