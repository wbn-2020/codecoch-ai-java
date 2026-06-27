package com.codecoachai.resume.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.redis.lock.DistributedLockHelper;
import com.codecoachai.resume.config.ResumeParseTaskProperties;
import com.codecoachai.resume.service.ResumeAnalysisParseService;
import org.junit.jupiter.api.Test;

class ResumeAnalysisParseTaskTest {

    @Test
    void parsePendingRecordsDoesNotParseWhenScheduleLockIsNotAcquired() {
        ResumeParseTaskProperties properties = new ResumeParseTaskProperties();
        ResumeAnalysisParseService parseService = mock(ResumeAnalysisParseService.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenReturn(false);
        ResumeAnalysisParseTask task = new ResumeAnalysisParseTask(properties, parseService, lockHelper);

        task.parsePendingRecords();

        verify(lockHelper).tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class));
        verify(parseService, never()).parsePendingRecords(any(Integer.class));
    }
}
