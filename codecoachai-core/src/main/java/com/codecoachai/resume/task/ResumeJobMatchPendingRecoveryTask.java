package com.codecoachai.resume.task;

import com.codecoachai.resume.service.ResumeJobMatchService;
import com.codecoachai.resume.domain.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.service.AsyncTaskService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/**
 * Converges reports whose dispatch was accepted but never claimed by a consumer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeJobMatchPendingRecoveryTask {

    private static final String BIZ_TYPE = "resume-job-match.analyze";
    private static final int BATCH_SIZE = 50;
    private final AsyncTaskService asyncTaskService;
    private final ResumeJobMatchService resumeJobMatchService;

    @Value("${codecoachai.resume.job-match.pending-timeout-ms:300000}")
    private long pendingTimeoutMillis;

    @Scheduled(fixedDelayString = "${codecoachai.resume.job-match.pending-recovery-delay-ms:60000}")
    public void recoverUnclaimedDispatches() {
        LocalDateTime before = LocalDateTime.now()
                .minus(Duration.ofMillis(Math.max(1L, pendingTimeoutMillis)));
        List<AsyncTask> staleTasks = asyncTaskService.findStalePending(BIZ_TYPE, before, BATCH_SIZE);
        for (AsyncTask task : staleTasks) {
            recover(task);
        }
    }

    private void recover(AsyncTask task) {
        if (task == null || task.getMessageId() == null || task.getBizId() == null) {
            return;
        }
        Long reportId;
        try {
            reportId = Long.valueOf(task.getBizId());
        } catch (NumberFormatException ignored) {
            log.error("Skip stale job match task with invalid business id taskId={} bizId={}",
                    task.getId(), task.getBizId());
            return;
        }
        String reason = "resume job match dispatch was not claimed before timeout";
        if (!asyncTaskService.failPendingIfUnclaimed(task.getMessageId(), reason)) {
            return;
        }
        try {
            ResumeJobMatchSubmitVO report = resumeJobMatchService.failExecution(reportId, reason);
            if (report == null || !"FAILED".equalsIgnoreCase(report.getStatus())) {
                throw new IllegalStateException("stale resume job match report failure writeback did not converge"
                        + " reportId=" + reportId + " taskId=" + task.getId());
            }
        } catch (RuntimeException callbackError) {
            try {
                asyncTaskService.prepareManualRetry(task.getId(), task.getMessageId());
            } catch (RuntimeException compensationError) {
                callbackError.addSuppressed(compensationError);
            }
            throw callbackError;
        }
    }
}
