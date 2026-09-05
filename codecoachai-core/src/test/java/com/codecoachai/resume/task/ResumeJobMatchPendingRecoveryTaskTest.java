package com.codecoachai.resume.task;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.codecoachai.resume.domain.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.resume.service.ResumeJobMatchService;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.service.AsyncTaskService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ResumeJobMatchPendingRecoveryTaskTest {

    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private ResumeJobMatchService resumeJobMatchService;

    private ResumeJobMatchPendingRecoveryTask task;

    @BeforeEach
    void setUp() {
        task = new ResumeJobMatchPendingRecoveryTask(asyncTaskService, resumeJobMatchService);
        ReflectionTestUtils.setField(task, "pendingTimeoutMillis", 300_000L);
    }

    @Test
    void recoversOnlyTasksReturnedByTheStaleUnclaimedPendingQuery() {
        AsyncTask staleTask = staleTask("msg-resume-job-match-pending-1", "88");
        when(asyncTaskService.findStalePending(
                eq("resume-job-match.analyze"), any(LocalDateTime.class), eq(50)))
                .thenReturn(List.of(staleTask));
        when(asyncTaskService.failPendingIfUnclaimed(eq("msg-resume-job-match-pending-1"), any()))
                .thenReturn(true);
        when(resumeJobMatchService.failExecution(eq(88L), any())).thenReturn(failedReport(88L));

        LocalDateTime beforeRecovery = LocalDateTime.now();
        task.recoverUnclaimedDispatches();
        LocalDateTime afterRecovery = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(asyncTaskService).findStalePending(
                eq("resume-job-match.analyze"), cutoffCaptor.capture(), eq(50));
        assertTrue(cutoffCaptor.getValue().isAfter(beforeRecovery.minusMinutes(6)));
        assertTrue(cutoffCaptor.getValue().isBefore(afterRecovery.minusMinutes(4)));
        InOrder order = inOrder(resumeJobMatchService, asyncTaskService);
        order.verify(asyncTaskService).failPendingIfUnclaimed(
                eq("msg-resume-job-match-pending-1"), any());
        order.verify(resumeJobMatchService).failExecution(eq(88L), any());
    }

    @Test
    void skipsBusinessFailureWritebackWhenPendingCasTransitionIsLost() {
        AsyncTask staleTask = staleTask("msg-resume-job-match-pending-2", "89");
        when(asyncTaskService.findStalePending(
                eq("resume-job-match.analyze"), any(LocalDateTime.class), eq(50)))
                .thenReturn(List.of(staleTask));
        when(asyncTaskService.failPendingIfUnclaimed(eq("msg-resume-job-match-pending-2"), any()))
                .thenReturn(false);

        task.recoverUnclaimedDispatches();

        verify(asyncTaskService).failPendingIfUnclaimed(
                eq("msg-resume-job-match-pending-2"), any());
        verify(resumeJobMatchService, never()).failExecution(any(), any());
    }

    @Test
    void reportFailureWritebackFailureLeavesPendingTaskUnfinishedAndRethrows() {
        AsyncTask staleTask = staleTask("msg-resume-job-match-pending-3", "90");
        when(asyncTaskService.findStalePending(
                eq("resume-job-match.analyze"), any(LocalDateTime.class), eq(50)))
                .thenReturn(List.of(staleTask));
        when(resumeJobMatchService.failExecution(eq(90L), any()))
                .thenThrow(new IllegalStateException("resume database unavailable"));
        when(asyncTaskService.failPendingIfUnclaimed(eq("msg-resume-job-match-pending-3"), any()))
                .thenReturn(true);

        assertThrows(RuntimeException.class, () -> task.recoverUnclaimedDispatches());

        verify(resumeJobMatchService).failExecution(eq(90L), any());
        verify(asyncTaskService).failPendingIfUnclaimed(eq("msg-resume-job-match-pending-3"), any());
        verify(asyncTaskService).prepareManualRetry(101L, "msg-resume-job-match-pending-3");
    }

    private ResumeJobMatchSubmitVO failedReport(Long reportId) {
        ResumeJobMatchSubmitVO result = new ResumeJobMatchSubmitVO();
        result.setReportId(reportId);
        result.setStatus("FAILED");
        return result;
    }

    private AsyncTask staleTask(String messageId, String reportId) {
        AsyncTask task = new AsyncTask();
        task.setId(101L);
        task.setMessageId(messageId);
        task.setBizType("resume-job-match.analyze");
        task.setBizId(reportId);
        task.setStatus("PENDING");
        task.setLeaseToken(null);
        task.setCreatedAt(LocalDateTime.now().minusMinutes(6));
        return task;
    }
}
