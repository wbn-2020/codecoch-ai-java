package com.codecoachai.resume.careerimport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.resume.careerimport.CareerImportModels.DuplicateCandidate;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.careerimport.CareerImportDedupeGuardMapper;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerImportApplicationWriter {

    private final CareerImportDedupeGuardMapper guardMapper;
    private final JobApplicationMapper applicationMapper;
    private final CareerImportCanonicalSupport canonicalSupport;

    @Transactional(
            propagation = Propagation.REQUIRED,
            noRollbackFor = DuplicateKeyException.class)
    public WriteOutcome writeSkip(JobApplication application) {
        String guardIdentityHash = canonicalSupport.guardIdentityHash(application);
        application.setImportFingerprint(canonicalSupport.importFingerprint(application));
        if (guardIdentityHash == null) {
            return insert(application);
        }

        guardMapper.insertIgnore(application.getUserId(), guardIdentityHash);
        guardMapper.selectForUpdate(application.getUserId(), guardIdentityHash);
        List<JobApplication> candidates = loadFreshCandidates(application);
        if (candidates.size() > 1000) {
            throw new IllegalStateException(
                    "Too many active application candidates to safely evaluate duplicates");
        }
        List<DuplicateCandidate> duplicates =
                canonicalSupport.findDuplicates(application, candidates);
        if (!duplicates.isEmpty()) {
            return WriteOutcome.duplicate(duplicates);
        }
        return insert(application);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            noRollbackFor = DuplicateKeyException.class)
    public WriteOutcome writeCreate(JobApplication application) {
        application.setImportFingerprint(null);
        return insert(application);
    }

    String guardIdentityHash(JobApplication application) {
        return canonicalSupport.guardIdentityHash(application);
    }

    private List<JobApplication> loadFreshCandidates(JobApplication application) {
        if (application.getAppliedAt() == null) {
            return applicationMapper.selectCareerImportCandidatesForUndated(
                    application.getUserId());
        }
        LocalDate appliedDate = application.getAppliedAt().toLocalDate();
        LocalDateTime startAt = appliedDate.minusDays(7).atStartOfDay();
        LocalDateTime endAt = appliedDate.plusDays(7).atTime(LocalTime.MAX);
        return applicationMapper.selectCareerImportCandidatesInDateWindow(
                application.getUserId(), startAt, endAt);
    }

    private WriteOutcome insert(JobApplication application) {
        try {
            applicationMapper.insert(application);
            return WriteOutcome.inserted(application.getId());
        } catch (DuplicateKeyException ex) {
            if (isMysqlDuplicateKey(ex)
                    && hasMatchingFingerprintWinner(application)) {
                return WriteOutcome.concurrentDuplicate();
            }
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    private boolean isMysqlDuplicateKey(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "23000".equals(sqlException.getSQLState())
                    && sqlException.getErrorCode() == 1062) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMatchingFingerprintWinner(JobApplication application) {
        if (application.getImportFingerprint() == null) {
            return false;
        }
        JobApplication winner = applicationMapper.selectOne(
                new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getUserId, application.getUserId())
                        .eq(JobApplication::getImportFingerprint, application.getImportFingerprint())
                        .eq(JobApplication::getDeleted, CommonConstants.NO)
                        .last("limit 1 for update"));
        return winner != null
                && application.getUserId().equals(winner.getUserId())
                && application.getImportFingerprint().equals(winner.getImportFingerprint());
    }

    public record WriteOutcome(
            String disposition,
            Long applicationId,
            List<DuplicateCandidate> duplicateCandidates,
            String errorCode,
            String errorMessage) {

        static WriteOutcome inserted(Long applicationId) {
            return new WriteOutcome("INSERTED", applicationId, List.of(), null, null);
        }

        static WriteOutcome duplicate(List<DuplicateCandidate> candidates) {
            return new WriteOutcome(
                    "SKIPPED_DUPLICATE", null, List.copyOf(candidates), null, null);
        }

        static WriteOutcome concurrentDuplicate() {
            return new WriteOutcome(
                    "SKIPPED_DUPLICATE",
                    null,
                    List.of(),
                    "CONCURRENT_DUPLICATE",
                    "The same application row was imported concurrently");
        }

        static WriteOutcome failed(String message) {
            return new WriteOutcome(
                    "ERROR", null, List.of(), "APPLICATION_INSERT_FAILED", message);
        }
    }
}
