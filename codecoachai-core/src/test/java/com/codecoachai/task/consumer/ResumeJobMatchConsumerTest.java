package com.codecoachai.task.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.ResumeJobMatchPayload;
import com.codecoachai.task.feign.ResumeFeignClient;
import com.codecoachai.task.feign.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.service.AsyncTaskService;
import com.codecoachai.task.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeJobMatchConsumerTest {

    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private ResumeFeignClient resumeFeignClient;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AsyncTaskMapper asyncTaskMapper;

    private ResumeJobMatchConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ResumeJobMatchConsumer(
                asyncTaskService, resumeFeignClient, notificationService, asyncTaskMapper);
    }

    @Test
    void skipsBusinessProcessingWhenRegisteredTaskCannotBeAcquired() {
        MqMessage<ResumeJobMatchPayload> envelope = envelope();
        when(asyncTaskService.acquireRegistered(envelope, 3)).thenReturn(false);

        consumer.onMessage(envelope);

        verify(asyncTaskService).acquireRegistered(envelope, 3);
        verify(asyncTaskService, never()).markSuccess(any(), any());
        verify(asyncTaskService, never()).markTerminalFailed(any(), any());
        verify(asyncTaskService, never()).markDead(any(), any());
        verify(asyncTaskService, never()).markFailed(any(), any());
        verifyNoInteractions(resumeFeignClient, notificationService);
    }

    @Test
    void trustedSuccessCompletesTheAsyncTaskAndNotifiesTheUser() {
        MqMessage<ResumeJobMatchPayload> envelope = envelope();
        ResumeJobMatchSubmitVO report = trustedSuccess();
        when(asyncTaskService.acquireRegistered(envelope, 3)).thenReturn(true);
        when(resumeFeignClient.executeJobMatchReport(88L)).thenReturn(Result.success(report));

        consumer.onMessage(envelope);

        verify(asyncTaskService).markSuccess("msg-resume-job-match-1", report);
        verify(asyncTaskService, never()).markTerminalFailed(any(), any());
        verify(asyncTaskService, never()).markDead(any(), any());
        verify(resumeFeignClient, never()).failJobMatchReport(any(), any());
        verify(notificationService).notifyTaskDone(
                eq(10L), eq("RESUME_JOB_MATCH"), eq("88"), any(), any());
        verify(notificationService, never()).notifyTaskFailed(any(), any(), any(), any(), any());
    }

    @Test
    void nonRetryableFailureConvergesBusinessReportAndAsyncTask() {
        MqMessage<ResumeJobMatchPayload> envelope = envelope();
        when(asyncTaskService.acquireRegistered(envelope, 3)).thenReturn(true);
        when(resumeFeignClient.executeJobMatchReport(88L))
                .thenReturn(Result.fail(ErrorCode.PARAM_ERROR.getCode(), "invalid report state"));
        when(resumeFeignClient.failJobMatchReport(eq(88L), any())).thenReturn(Result.success(failedReport()));

        consumer.onMessage(envelope);

        InOrder order = inOrder(resumeFeignClient, asyncTaskService);
        order.verify(resumeFeignClient).failJobMatchReport(eq(88L), any());
        order.verify(asyncTaskService).markTerminalFailed(eq("msg-resume-job-match-1"), any());
        verify(asyncTaskService, never()).markSuccess(any(), any());
        verify(asyncTaskService, never()).markFailed(any(), any());
        verify(notificationService).notifyTaskFailed(
                eq(10L), eq("RESUME_JOB_MATCH"), eq("88"), any(), any());
    }

    @Test
    void untrustedReportIsTerminallyFailedRatherThanReportedAsSuccess() {
        MqMessage<ResumeJobMatchPayload> envelope = envelope();
        ResumeJobMatchSubmitVO report = trustedSuccess();
        report.setTrustStatus("UNTRUSTED");
        report.setFallback(true);
        report.setSchemaWarningCount(1);
        report.setErrorMessage("generated evidence did not satisfy the trust policy");
        when(asyncTaskService.acquireRegistered(envelope, 3)).thenReturn(true);
        when(resumeFeignClient.executeJobMatchReport(88L)).thenReturn(Result.success(report));
        when(resumeFeignClient.failJobMatchReport(eq(88L), any())).thenReturn(Result.success(failedReport()));

        consumer.onMessage(envelope);

        verify(asyncTaskService).markTerminalFailed(eq("msg-resume-job-match-1"), any());
        verify(asyncTaskService, never()).markSuccess(any(), any());
        verify(resumeFeignClient).failJobMatchReport(eq(88L), any());
        verify(notificationService).notifyTaskFailed(
                eq(10L), eq("RESUME_JOB_MATCH"), eq("88"), any(), any());
    }

    @Test
    void retryBudgetExhaustionConvergesBusinessReportAndAsyncTask() {
        MqMessage<ResumeJobMatchPayload> envelope = envelope();
        when(asyncTaskService.acquireRegistered(envelope, 3)).thenReturn(true);
        when(resumeFeignClient.executeJobMatchReport(88L))
                .thenThrow(new IllegalStateException("upstream temporarily unavailable"));
        when(asyncTaskService.markFailed(any(), any())).thenReturn(true);
        when(resumeFeignClient.failJobMatchReport(eq(88L), any())).thenReturn(Result.success(failedReport()));
        when(asyncTaskMapper.selectOne(any())).thenReturn(runningTask(3, 3));

        assertThrows(RuntimeException.class, () -> consumer.onMessage(envelope));

        InOrder order = inOrder(resumeFeignClient, asyncTaskService);
        order.verify(resumeFeignClient).failJobMatchReport(eq(88L), any());
        order.verify(asyncTaskService).markFailed(eq("msg-resume-job-match-1"), any());
        verify(asyncTaskService, never()).markSuccess(any(), any());
        verify(asyncTaskService, never()).markTerminalFailed(any(), any());
        verify(notificationService).notifyTaskFailed(
                eq(10L), eq("RESUME_JOB_MATCH"), eq("88"), any(), any());
    }

    @Test
    void businessFailureWritebackFailureLeavesAsyncTaskRetryableAndRethrows() {
        MqMessage<ResumeJobMatchPayload> envelope = envelope();
        when(asyncTaskService.acquireRegistered(envelope, 3)).thenReturn(true);
        when(resumeFeignClient.executeJobMatchReport(88L))
                .thenReturn(Result.fail(ErrorCode.PARAM_ERROR.getCode(), "invalid report state"));
        when(resumeFeignClient.failJobMatchReport(eq(88L), any()))
                .thenReturn(Result.fail(500, "resume service unavailable"));
        when(asyncTaskService.markFailed(any(), any())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> consumer.onMessage(envelope));

        verify(resumeFeignClient).failJobMatchReport(eq(88L), any());
        verify(asyncTaskService, never()).markTerminalFailed(any(), any());
        verify(asyncTaskService).markFailed(eq("msg-resume-job-match-1"), any());
        verify(notificationService, never()).notifyTaskFailed(any(), any(), any(), any(), any());
    }

    @Test
    void terminalRetryFailureWritebackFailureRestoresTheAsyncTaskForRetry() {
        MqMessage<ResumeJobMatchPayload> envelope = envelope();
        when(asyncTaskService.acquireRegistered(envelope, 3)).thenReturn(true);
        when(resumeFeignClient.executeJobMatchReport(88L))
                .thenThrow(new IllegalStateException("upstream temporarily unavailable"));
        when(asyncTaskService.markFailed(any(), any())).thenReturn(true);
        when(resumeFeignClient.failJobMatchReport(eq(88L), any()))
                .thenReturn(Result.fail(500, "resume service unavailable"));
        when(asyncTaskMapper.selectOne(any()))
                .thenReturn(runningTask(3, 3), terminalTask("DEAD"));

        assertThrows(RuntimeException.class, () -> consumer.onMessage(envelope));

        InOrder order = inOrder(asyncTaskService, resumeFeignClient);
        order.verify(resumeFeignClient).failJobMatchReport(eq(88L), any());
        order.verify(asyncTaskService).markFailed(eq("msg-resume-job-match-1"), any());
        order.verify(asyncTaskService).prepareManualRetry(100L, "msg-resume-job-match-1");
        verify(notificationService, never()).notifyTaskFailed(any(), any(), any(), any(), any());
    }

    @Test
    void nonTerminalRetryFailureDoesNotWriteTheReportBackPrematurely() {
        MqMessage<ResumeJobMatchPayload> envelope = envelope();
        when(asyncTaskService.acquireRegistered(envelope, 3)).thenReturn(true);
        when(resumeFeignClient.executeJobMatchReport(88L))
                .thenThrow(new IllegalStateException("upstream temporarily unavailable"));
        when(asyncTaskMapper.selectOne(any())).thenReturn(runningTask(2, 3));
        when(asyncTaskService.markFailed(any(), any())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> consumer.onMessage(envelope));

        verify(asyncTaskService).markFailed(eq("msg-resume-job-match-1"), any());
        verify(resumeFeignClient, never()).failJobMatchReport(any(), any());
        verify(notificationService, never()).notifyTaskFailed(any(), any(), any(), any(), any());
    }

    @Test
    void unexpectedTerminalTransitionIsRestoredWithoutPostTerminalWriteback() {
        MqMessage<ResumeJobMatchPayload> envelope = envelope();
        when(asyncTaskService.acquireRegistered(envelope, 3)).thenReturn(true);
        when(resumeFeignClient.executeJobMatchReport(88L))
                .thenThrow(new IllegalStateException("upstream temporarily unavailable"));
        when(asyncTaskMapper.selectOne(any()))
                .thenReturn(runningTask(2, 3), terminalTask("DEAD"));
        when(asyncTaskService.markFailed(any(), any())).thenReturn(true);

        assertThrows(RuntimeException.class, () -> consumer.onMessage(envelope));

        verify(resumeFeignClient, never()).failJobMatchReport(any(), any());
        verify(asyncTaskService).prepareManualRetry(100L, "msg-resume-job-match-1");
        verify(notificationService, never()).notifyTaskFailed(any(), any(), any(), any(), any());
    }

    private ResumeJobMatchSubmitVO trustedSuccess() {
        ResumeJobMatchSubmitVO result = new ResumeJobMatchSubmitVO();
        result.setReportId(88L);
        result.setStatus("SUCCESS");
        result.setTrustStatus("VERIFIED");
        result.setFallback(false);
        result.setSchemaWarningCount(0);
        result.setAiCallLogId(99L);
        return result;
    }

    private ResumeJobMatchSubmitVO failedReport() {
        ResumeJobMatchSubmitVO result = new ResumeJobMatchSubmitVO();
        result.setReportId(88L);
        result.setStatus("FAILED");
        return result;
    }

    private AsyncTask terminalTask(String status) {
        AsyncTask task = new AsyncTask();
        task.setId(100L);
        task.setMessageId("msg-resume-job-match-1");
        task.setStatus(status);
        return task;
    }

    private AsyncTask runningTask(int retryCount, int maxRetry) {
        AsyncTask task = terminalTask("RUNNING");
        task.setRetryCount(retryCount);
        task.setMaxRetry(maxRetry);
        return task;
    }

    private MqMessage<ResumeJobMatchPayload> envelope() {
        MqMessage<ResumeJobMatchPayload> message = new MqMessage<>();
        message.setMessageId("msg-resume-job-match-1");
        message.setTraceId("trace-resume-job-match-1");
        message.setBizType("resume-job-match.analyze");
        message.setBizId("88");
        message.setUserId(10L);
        message.setPayload(ResumeJobMatchPayload.builder()
                .reportId(88L)
                .userId(10L)
                .build());
        message.setRetryCount(0);
        return message;
    }
}
