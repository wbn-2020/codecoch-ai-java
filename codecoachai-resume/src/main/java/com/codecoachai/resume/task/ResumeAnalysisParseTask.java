package com.codecoachai.resume.task;

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

    private final ResumeParseTaskProperties properties;
    private final ResumeAnalysisParseService parseService;

    @Scheduled(fixedDelayString = "${codecoachai.resume.parse-task.fixedDelay:${codecoachai.resume.parse-task.fixed-delay:10000}}")
    public void parsePendingRecords() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            parseService.parsePendingRecords(properties.getBatchSize());
        } catch (RuntimeException ex) {
            log.error("Resume analysis parse task failed", ex);
        }
    }
}
