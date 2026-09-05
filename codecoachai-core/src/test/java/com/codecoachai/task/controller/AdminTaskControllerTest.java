package com.codecoachai.task.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.mq.payload.QuestionGeneratePayload;
import com.codecoachai.common.mq.producer.MqProducer;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.task.domain.dto.AdminTaskActionDTO;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import com.codecoachai.task.domain.vo.AdminTaskGovernancePreviewVO;
import com.codecoachai.task.domain.vo.AdminTaskStatsVO;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.mapper.MessageDeadLetterMapper;
import com.codecoachai.task.service.AsyncTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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
    void normalizesDashboardFailureStatusGroupForPageQuery() {
        assertEquals(
                List.of("FAILED", "DEAD", "ERROR", "DEAD_LETTER"),
                AdminTaskController.normalizeStatusFilter("failed, DEAD，error,failed,dead_letter"));
    }

    @Test
    void pageQueryAppliesDashboardFailureStatusesDeletedFilterAndSnapshotWindow() {
        initializeTableInfo(AsyncTask.class);
        LocalDateTime createdFrom = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime createdBefore = LocalDateTime.of(2026, 8, 18, 12, 0);
        Page<AsyncTask> page = new Page<>(1L, 20L, 0L);
        page.setRecords(List.of());
        when(asyncTaskMapper.selectPage(any(Page.class), any())).thenReturn(page);

        controllerWithoutProducer.pageTasks(
                1L,
                20L,
                null,
                null,
                null,
                AsyncTaskMapper.ADMIN_FAILURE_STATUS_FILTER,
                null,
                null,
                createdFrom,
                createdBefore);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AsyncTask>> captor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(asyncTaskMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("deleted"), sql);
        assertTrue(sql.contains("status in"), sql);
        assertTrue(sql.contains("created_at >="), sql);
        assertTrue(sql.contains("created_at <"), sql);
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(0));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("FAILED"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("DEAD"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("ERROR"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("DEAD_LETTER"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(createdFrom));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(createdBefore));
    }

    @Test
    void statsReturnsSameFailureScopeWindowAndNavigationQueryAsDashboard() {
        LocalDateTime createdBefore = LocalDateTime.of(2026, 8, 18, 12, 0);
        when(asyncTaskMapper.countAdminTasks(
                eq(AsyncTaskMapper.ADMIN_FAILURE_STATUSES),
                isNull(),
                eq(createdBefore)))
                .thenReturn(305L);
        when(asyncTaskMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(
                        Map.of("STATUS", "FAILED", "COUNT", 300L),
                        Map.of("STATUS", "DEAD", "COUNT", 5L)));

        AdminTaskStatsVO stats = controllerWithoutProducer.stats(
                AsyncTaskMapper.ADMIN_FAILURE_STATUS_FILTER,
                null,
                createdBefore).getData();

        assertEquals(305L, stats.getTotal());
        assertEquals(2, stats.getStatusCounts().size());
        assertEquals(300L, stats.getStatusCounts().get(0).getCount());
        assertEquals(AsyncTaskMapper.ADMIN_FAILURE_STATUSES, stats.getStatuses());
        assertEquals(AsyncTaskMapper.ADMIN_FAILURE_STATUS_FILTER, stats.getStatusFilter());
        assertEquals("ALL_TIME_TO_SNAPSHOT", stats.getWindowType());
        assertEquals(createdBefore, stats.getWindowEnd());
        assertEquals("Asia/Shanghai", stats.getBusinessTimezone());
        assertEquals("/admin/async-tasks", stats.getNavigationPath());
        assertEquals(AsyncTaskMapper.ADMIN_FAILURE_STATUS_FILTER, stats.getNavigationQuery().get("status"));
        assertEquals(createdBefore.toString(), stats.getNavigationQuery().get("createdBefore"));
        verify(asyncTaskMapper).countAdminTasks(
                eq(AsyncTaskMapper.ADMIN_FAILURE_STATUSES),
                isNull(),
                eq(createdBefore));
    }

    @Test
    void taskWindowRejectsEmptyOrReversedRangeBeforeQuerying() {
        LocalDateTime boundary = LocalDateTime.of(2026, 8, 18, 12, 0);

        assertThrows(BusinessException.class, () -> controllerWithoutProducer.stats(
                AsyncTaskMapper.ADMIN_FAILURE_STATUS_FILTER,
                boundary,
                boundary));

        verify(asyncTaskMapper, never()).countAdminTasks(any(), any(), any());
        verify(asyncTaskMapper, never()).selectMaps(any());
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
        verify(asyncTaskService, never()).prepareManualRetry(
                any(AsyncTask.class), any(AsyncTaskService.ManualRetryAttempt.class));
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
        AsyncTask task = failedQuestionTask();
        task.setGovernanceStatus("RETRY_APPROVED");
        task.setUpdatedAt(LocalDateTime.of(2026, 8, 15, 9, 0));
        when(asyncTaskMapper.selectById(7L)).thenReturn(task);
        String previewHash = controllerWithoutProducer.retryTaskPreview(7L).getData().getPreviewHash();
        task.setRetryPreviewHash(previewHash);
        dto.setPreviewHash(previewHash);
        AsyncTask retryTask = new AsyncTask();
        retryTask.setId(17L);
        retryTask.setExecutionId("retry:7:child");
        when(asyncTaskService.prepareManualRetry(
                any(AsyncTask.class), any(AsyncTaskService.ManualRetryAttempt.class)))
                .thenReturn(retryTask);

        assertThrows(BusinessException.class, () -> controllerWithoutProducer.retryTask(7L, dto));

        verify(asyncTaskService).prepareManualRetry(
                eq(task), any(AsyncTaskService.ManualRetryAttempt.class));
        verify(asyncTaskService).markManualRetryDispatchFailed(
                eq(7L), eq(17L), eq("retry:7:child"), anyString());
        verify(operationConfirmationGuard).release("lock-key");
    }

    @Test
    void retryTaskRejectsDuplicateConfirmationBeforeLoadingOrDispatching() {
        AdminTaskActionDTO dto = confirmedDto("admin-task-retry-duplicate");
        when(operationConfirmationGuard.requireConfirmed(
                eq("async-task-retry:7"),
                eq(true),
                eq(false),
                eq(dto.getReason()),
                eq(dto.getIdempotencyKey())))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "duplicate"));

        assertThrows(BusinessException.class, () -> controllerWithProducer.retryTask(7L, dto));

        verify(asyncTaskMapper, never()).selectById(any());
        verify(asyncTaskService, never()).prepareManualRetry(
                any(AsyncTask.class), any(AsyncTaskService.ManualRetryAttempt.class));
        verify(mqProducer, never()).sendEnvelopeSync(anyString(), any());
    }

    @Test
    void retryPreviewIsImmediatelyValidAfterApprovalAndStaleAfterTaskVersionChanges() {
        AsyncTask task = failedQuestionTask();
        task.setUpdatedAt(LocalDateTime.of(2026, 8, 17, 10, 0));
        when(asyncTaskMapper.selectById(7L)).thenReturn(task);
        AdminTaskGovernancePreviewVO governancePreview = controllerWithoutProducer.governancePreview(7L).getData();
        AdminTaskActionDTO dto = governanceDto("RETRY_APPROVED", governancePreview.getPreviewHash());
        when(operationConfirmationGuard.requireConfirmed(
                eq("async-task-governance:7"),
                eq(true),
                eq(false),
                eq(dto.getReason()),
                eq(dto.getIdempotencyKey())))
                .thenReturn("lock-key");
        ArgumentCaptor<String> approvedHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> approvedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        when(asyncTaskMapper.updateGovernance(
                eq(7L),
                eq("RETRY_APPROVED"),
                eq(dto.getNote()),
                eq(dto.getGovernanceOwner()),
                approvedHash.capture(),
                eq(task.getUpdatedAt()),
                approvedAt.capture()))
                .thenReturn(1);

        controllerWithoutProducer.updateTaskGovernance(7L, dto);

        task.setGovernanceStatus("RETRY_APPROVED");
        task.setRetryPreviewHash(approvedHash.getValue());
        task.setUpdatedAt(approvedAt.getValue().withNano(0));
        String immediateHash = controllerWithoutProducer.retryTaskPreview(7L).getData().getPreviewHash();
        assertEquals(approvedHash.getValue(), immediateHash);

        task.setUpdatedAt(task.getUpdatedAt().plusSeconds(1));
        String changedVersionHash = controllerWithoutProducer.retryTaskPreview(7L).getData().getPreviewHash();
        assertNotEquals(immediateHash, changedVersionHash);
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

    @Test
    void governanceUpdateRejectsMissingConfirmationBeforeLoadingTask() {
        AdminTaskActionDTO dto = governanceDto("MANUAL_ACTION_REQUIRED", "preview-hash");
        dto.setConfirm(null);
        dto.setReason(null);
        dto.setIdempotencyKey(null);
        when(operationConfirmationGuard.requireConfirmed(
                eq("async-task-governance:7"),
                isNull(),
                eq(false),
                isNull(),
                isNull()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class, () -> controllerWithoutProducer.updateTaskGovernance(7L, dto));

        verify(permissionGuard).require("admin:task:retry");
        verify(asyncTaskMapper, never()).selectById(any());
        verify(asyncTaskMapper, never()).updateGovernance(any(), anyString(), anyString(), any(), anyString(), any(), any());
        verify(asyncTaskService, never()).prepareManualRetry(
                any(AsyncTask.class), any(AsyncTaskService.ManualRetryAttempt.class));
    }

    @Test
    void governanceUpdateRejectsStalePreviewHashWithoutWritingOrDispatching() {
        AdminTaskActionDTO dto = governanceDto("MANUAL_ACTION_REQUIRED", "stale-preview-hash");
        when(operationConfirmationGuard.requireConfirmed(
                eq("async-task-governance:7"),
                eq(true),
                eq(false),
                eq(dto.getReason()),
                eq(dto.getIdempotencyKey())))
                .thenReturn("lock-key");
        assertThrows(BusinessException.class, () -> controllerWithoutProducer.updateTaskGovernance(7L, dto));

        verify(asyncTaskMapper, never()).updateGovernance(any(), anyString(), anyString(), any(), anyString(), any(), any());
        verify(asyncTaskService, never()).prepareManualRetry(
                any(AsyncTask.class), any(AsyncTaskService.ManualRetryAttempt.class));
        verify(operationConfirmationGuard).release("lock-key");
    }

    @Test
    void governanceInventoryIsReadOnlyAndBounded() {
        when(asyncTaskMapper.selectList(any())).thenReturn(List.of(failedQuestionTask()));

        var result = controllerWithoutProducer.governanceInventory(null, null, null, null, 500);

        assertEquals(1, result.getData().size());
        verify(permissionGuard).require("admin:task:list");
        verify(asyncTaskService, never()).prepareManualRetry(
                any(AsyncTask.class), any(AsyncTaskService.ManualRetryAttempt.class));
        verify(asyncTaskService, never()).markManualRetryDispatchFailed(any(), any(), any(), anyString());
    }

    @Test
    void governanceUpdateUsesPreviewHashAndUpdatedAtCas() {
        AsyncTask task = failedQuestionTask();
        task.setUpdatedAt(LocalDateTime.of(2026, 8, 15, 9, 0));
        when(asyncTaskMapper.selectById(7L)).thenReturn(task);
        AdminTaskGovernancePreviewVO preview = controllerWithoutProducer.governancePreview(7L).getData();
        AdminTaskActionDTO dto = governanceDto("MANUAL_ACTION_REQUIRED", preview.getPreviewHash());
        when(operationConfirmationGuard.requireConfirmed(
                eq("async-task-governance:7"),
                eq(true),
                eq(false),
                eq(dto.getReason()),
                eq(dto.getIdempotencyKey())))
                .thenReturn("lock-key");
        when(asyncTaskMapper.updateGovernance(
                eq(7L),
                eq("MANUAL_ACTION_REQUIRED"),
                eq("dependency requires manual remediation"),
                eq("PLATFORM_ONCALL"),
                eq(preview.getPreviewHash()),
                eq(task.getUpdatedAt()),
                any(LocalDateTime.class)))
                .thenReturn(1);

        controllerWithoutProducer.updateTaskGovernance(7L, dto);

        verify(asyncTaskMapper).updateGovernance(
                eq(7L),
                eq("MANUAL_ACTION_REQUIRED"),
                eq("dependency requires manual remediation"),
                eq("PLATFORM_ONCALL"),
                eq(preview.getPreviewHash()),
                eq(task.getUpdatedAt()),
                any(LocalDateTime.class));
        verify(asyncTaskService, never()).prepareManualRetry(
                any(AsyncTask.class), any(AsyncTaskService.ManualRetryAttempt.class));
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

    private static AdminTaskActionDTO governanceDto(String governanceStatus, String previewHash) {
        AdminTaskActionDTO dto = confirmedDto("admin-task-governance-1234");
        dto.setNote("dependency requires manual remediation");
        dto.setReason("governance classification confirmed");
        dto.setGovernanceStatus(governanceStatus);
        dto.setGovernanceOwner("PLATFORM_ONCALL");
        dto.setPreviewHash(previewHash);
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

    private static void initializeTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
