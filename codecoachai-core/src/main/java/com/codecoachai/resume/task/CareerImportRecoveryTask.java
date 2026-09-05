package com.codecoachai.resume.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.resume.careerimport.entity.CareerImportBatch;
import com.codecoachai.resume.mapper.careerimport.CareerImportBatchMapper;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CareerImportRecoveryTask {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_FAILED = "FAILED";

    private final CareerImportBatchMapper batchMapper;
    private final long staleAfterMinutes;

    public CareerImportRecoveryTask(
            CareerImportBatchMapper batchMapper,
            @Value("${codecoachai.resume.career-import.recovery.stale-after-minutes:30}")
            long staleAfterMinutes) {
        if (staleAfterMinutes < 5 || staleAfterMinutes > 24 * 60) {
            throw new IllegalArgumentException("career import recovery timeout must be between 5 and 1440 minutes");
        }
        this.batchMapper = batchMapper;
        this.staleAfterMinutes = staleAfterMinutes;
    }

    @Scheduled(
            initialDelayString = "${codecoachai.resume.career-import.recovery.initial-delay:15000}",
            fixedDelayString = "${codecoachai.resume.career-import.recovery.fixed-delay:60000}")
    public void recover() {
        try {
            int recovered = recoverStaleImports(LocalDateTime.now());
            if (recovered > 0) {
                log.warn("Recovered stale career import batches count={} terminalStatus={}",
                        recovered, STATUS_FAILED);
            }
        } catch (RuntimeException ex) {
            log.error("Career import recovery failed exceptionType={}",
                    ex.getClass().getSimpleName());
        }
    }

    int recoverStaleImports(LocalDateTime now) {
        LocalDateTime cutoff = now.minusMinutes(staleAfterMinutes);
        return batchMapper.update(null, new LambdaUpdateWrapper<CareerImportBatch>()
                .set(CareerImportBatch::getStatus, STATUS_FAILED)
                .set(CareerImportBatch::getUpdatedAt, now)
                .eq(CareerImportBatch::getStatus, STATUS_RUNNING)
                .eq(CareerImportBatch::getDeleted, CommonConstants.NO)
                .lt(CareerImportBatch::getUpdatedAt, cutoff));
    }
}
