package com.codecoachai.resume.task;

import com.codecoachai.resume.service.EvidenceProfileFeedbackOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceProfileFeedbackOutboxTask {

    private final EvidenceProfileFeedbackOutboxService outboxService;

    @Scheduled(fixedDelayString = "${codecoachai.resume.evidence-feedback-outbox.fixed-delay:10000}")
    public void retryPending() {
        try {
            int processed = outboxService.retryPending(50);
            if (processed > 0) {
                log.info("Evidence feedback outbox retry processed={}", processed);
            }
        } catch (RuntimeException ex) {
            log.error("Evidence feedback outbox retry task failed", ex);
        }
    }
}
