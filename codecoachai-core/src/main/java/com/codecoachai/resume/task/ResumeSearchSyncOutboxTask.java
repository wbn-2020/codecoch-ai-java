package com.codecoachai.resume.task;

import com.codecoachai.resume.service.ResumeSearchSyncOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeSearchSyncOutboxTask {

    private final ResumeSearchSyncOutboxService outboxService;

    @Scheduled(fixedDelayString = "${codecoachai.resume.search-outbox.fixed-delay:10000}")
    public void retryPending() {
        try {
            int processed = outboxService.retryPending(50);
            if (processed > 0) {
                log.info("Resume search outbox retry processed={}", processed);
            }
        } catch (RuntimeException ex) {
            log.error("Resume search outbox retry task failed", ex);
        }
    }
}
