package com.codecoachai.resume.task;

import com.codecoachai.common.redis.constant.RedisKeyConstants;
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import com.codecoachai.resume.config.ResumeParseTaskProperties;
import com.codecoachai.resume.service.ResumeAnalysisParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeAnalysisParseTask {

    private static final String RESUME_PARSE_SCHEDULE_LOCK_KEY = RedisKeyConstants.LOCK_RESUME_PARSE_PREFIX + "schedule";

    private final ResumeParseTaskProperties properties;
    private final ResumeAnalysisParseService parseService;
    private final DistributedLockHelper distributedLockHelper;

    @Scheduled(fixedDelayString = "${codecoachai.resume.parse-task.fixedDelay:${codecoachai.resume.parse-task.fixed-delay:10000}}")
    public void parsePendingRecords() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            boolean acquired = distributedLockHelper.tryLockAndRun(
                    RESUME_PARSE_SCHEDULE_LOCK_KEY,
                    properties.getLockWaitSeconds(),
                    properties.getLockLeaseSeconds(),
                    () -> parseService.parsePendingRecords(properties.getBatchSize()));
            if (!acquired) {
                log.info("Resume analysis parse task skipped because another scheduler run is active");
            }
        } catch (RuntimeException ex) {
            log.error("Resume analysis parse task failed", ex);
        }
    }
}
