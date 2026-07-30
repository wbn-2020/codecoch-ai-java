package com.codecoachai.interview.service.impl;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.config.InterviewCloneClaimProperties;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.entity.InterviewRemediation;
import com.codecoachai.interview.domain.entity.InterviewReplay;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.mapper.InterviewRemediationMapper;
import com.codecoachai.interview.mapper.InterviewReplayMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewCloneTransactionService {

    private static final String STATUS_CREATING = "CREATING";
    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_FAILED = "FAILED";
    private static final Duration DEFAULT_CLAIM_TIMEOUT = Duration.ofMinutes(2);

    private final InterviewReplayMapper replayMapper;
    private final InterviewRemediationMapper remediationMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewServiceImpl interviewService;
    private final InterviewCloneClaimProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public InterviewReplay recoverCompletedReplay(InterviewReplay candidate) {
        InterviewReplay existing = replayMapper.selectActiveByIdempotencyKeyForUpdate(
                candidate.getUserId(), candidate.getIdempotencyKey());
        if (existing == null) {
            return null;
        }
        validateReplayPayload(existing, candidate);
        return isCompleted(existing.getStatus(), existing.getTargetSessionId())
                ? existing : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public InterviewRemediation recoverCompletedRemediation(
            InterviewRemediation candidate) {
        InterviewRemediation existing =
                remediationMapper.selectActiveByIdempotencyKeyForUpdate(
                        candidate.getUserId(), candidate.getIdempotencyKey());
        if (existing == null) {
            return null;
        }
        validateRemediationPayload(existing, candidate);
        return isCompleted(existing.getStatus(), existing.getTargetSessionId())
                ? existing : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReplayClaim claimReplay(InterviewReplay candidate) {
        LocalDateTime now = LocalDateTime.now();
        String claimToken = newClaimToken();
        candidate.setStatus(STATUS_CREATING);
        candidate.setClaimToken(claimToken);
        candidate.setClaimedAt(now);
        try {
            if (replayMapper.insert(candidate) != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "再练 claim 创建失败");
            }
            return new ReplayClaim(candidate, claimToken, true);
        } catch (DuplicateKeyException ex) {
            InterviewReplay existing = replayMapper.selectActiveByIdempotencyKeyForUpdate(
                    candidate.getUserId(), candidate.getIdempotencyKey());
            if (existing == null) {
                throw ex;
            }
            validateReplayPayload(existing, candidate);
            if (isCompleted(existing.getStatus(), existing.getTargetSessionId())) {
                return new ReplayClaim(existing, null, false);
            }
            if (isFreshCreating(existing.getStatus(), existing.getClaimedAt(), now)) {
                throw creationInProgress();
            }
            if (!isRecoverable(existing.getStatus(), existing.getTargetSessionId())) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "再练记录状态不可恢复");
            }
            if (replayMapper.replaceClaim(existing, claimToken, now) != 1) {
                throw creationInProgress();
            }
            existing.setStatus(STATUS_CREATING);
            existing.setClaimToken(claimToken);
            existing.setClaimedAt(now);
            return new ReplayClaim(existing, claimToken, true);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public RemediationClaim claimRemediation(InterviewRemediation candidate) {
        LocalDateTime now = LocalDateTime.now();
        String claimToken = newClaimToken();
        candidate.setStatus(STATUS_CREATING);
        candidate.setClaimToken(claimToken);
        candidate.setClaimedAt(now);
        try {
            if (remediationMapper.insert(candidate) != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "复练 claim 创建失败");
            }
            return new RemediationClaim(candidate, claimToken, true);
        } catch (DuplicateKeyException ex) {
            InterviewRemediation existing =
                    remediationMapper.selectActiveByIdempotencyKeyForUpdate(
                            candidate.getUserId(), candidate.getIdempotencyKey());
            if (existing == null) {
                throw ex;
            }
            validateRemediationPayload(existing, candidate);
            if (isCompleted(existing.getStatus(), existing.getTargetSessionId())) {
                return new RemediationClaim(existing, null, false);
            }
            if (isFreshCreating(existing.getStatus(), existing.getClaimedAt(), now)) {
                throw creationInProgress();
            }
            if (!isRecoverable(existing.getStatus(), existing.getTargetSessionId())) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "复练记录状态不可恢复");
            }
            if (remediationMapper.replaceClaim(existing, claimToken, now) != 1) {
                throw creationInProgress();
            }
            existing.setStatus(STATUS_CREATING);
            existing.setClaimToken(claimToken);
            existing.setClaimedAt(now);
            return new RemediationClaim(existing, claimToken, true);
        }
    }

    public InterviewServiceImpl.InterviewClonePreparation prepareCloneTarget(
            CreateInterviewDTO request, Long sourceSessionId) {
        return interviewService.prepareClone(request, sourceSessionId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReplayCreation createReplayTarget(
            Long replayId,
            String claimToken,
            InterviewServiceImpl.InterviewClonePreparation preparation) {
        InterviewReplay replay = replayMapper.selectOwnedClaimForUpdate(replayId, claimToken);
        if (replay == null) {
            throw claimLost();
        }
        if (preparation == null
                || preparation.sourceSession() == null
                || !Objects.equals(
                        replay.getSourceSessionId(),
                        preparation.sourceSession().getId())) {
            throw claimLost();
        }
        CreateInterviewVO interview =
                interviewService.createPreparedClone(preparation);
        requireCreatedInterview(interview, "同配置再练创建失败");
        if (replayMapper.markCreated(replay.getId(), claimToken, interview.getId()) != 1) {
            throw claimLost();
        }
        replay.setTargetSessionId(interview.getId());
        replay.setStatus(STATUS_CREATED);
        replay.setClaimToken(null);
        replay.setClaimedAt(null);
        return new ReplayCreation(replay, interview);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public RemediationCreation createRemediationTarget(
            Long remediationId,
            String claimToken,
            InterviewServiceImpl.InterviewClonePreparation preparation) {
        InterviewRemediation remediation =
                remediationMapper.selectOwnedClaimForUpdate(remediationId, claimToken);
        if (remediation == null) {
            throw claimLost();
        }
        if (preparation == null
                || preparation.sourceSession() == null
                || !Objects.equals(
                        remediation.getSourceSessionId(),
                        preparation.sourceSession().getId())) {
            throw claimLost();
        }
        CreateInterviewVO interview =
                interviewService.createPreparedClone(preparation);
        requireCreatedInterview(interview, "复练面试创建失败");

        InterviewSession targetPatch = new InterviewSession();
        targetPatch.setId(interview.getId());
        targetPatch.setSourceReportId(remediation.getSourceReportId());
        targetPatch.setSourceRequirementIds(remediation.getSourceRequirementIds());
        targetPatch.setPracticePurpose(remediation.getPracticePurpose());
        targetPatch.setRemediationStrength(remediation.getRemediationStrength());
        if (sessionMapper.updateById(targetPatch) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "复练来源上下文保存失败");
        }
        if (remediationMapper.markCreated(
                remediation.getId(), claimToken, interview.getId()) != 1) {
            throw claimLost();
        }
        remediation.setTargetSessionId(interview.getId());
        remediation.setStatus(STATUS_CREATED);
        remediation.setClaimToken(null);
        remediation.setClaimedAt(null);
        return new RemediationCreation(remediation, interview);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean releaseReplayClaim(Long replayId, String claimToken) {
        return replayMapper.releaseClaim(replayId, claimToken) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean releaseRemediationClaim(Long remediationId, String claimToken) {
        return remediationMapper.releaseClaim(remediationId, claimToken) == 1;
    }

    private void validateReplayPayload(InterviewReplay existing, InterviewReplay candidate) {
        if (!Objects.equals(existing.getSourceSessionId(), candidate.getSourceSessionId())) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "幂等键已被不同的再练请求占用");
        }
    }

    private void validateRemediationPayload(
            InterviewRemediation existing, InterviewRemediation candidate) {
        if (!Objects.equals(existing.getSourceReportId(), candidate.getSourceReportId())
                || !Objects.equals(
                        existing.getSourceRequirementIds(), candidate.getSourceRequirementIds())
                || !Objects.equals(existing.getPracticePurpose(), candidate.getPracticePurpose())
                || !Objects.equals(
                        existing.getRemediationStrength(), candidate.getRemediationStrength())) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "幂等键已被不同的复练请求占用");
        }
    }

    private boolean isFreshCreating(
            String status, LocalDateTime claimedAt, LocalDateTime now) {
        return STATUS_CREATING.equalsIgnoreCase(status)
                && claimedAt != null
                && claimedAt.isAfter(now.minus(claimTimeout()));
    }

    private boolean isCompleted(String status, Long targetSessionId) {
        return STATUS_CREATED.equalsIgnoreCase(status) && targetSessionId != null;
    }

    private boolean isRecoverable(String status, Long targetSessionId) {
        return targetSessionId == null
                && (STATUS_FAILED.equalsIgnoreCase(status)
                        || STATUS_CREATING.equalsIgnoreCase(status)
                        || STATUS_CREATED.equalsIgnoreCase(status));
    }

    private Duration claimTimeout() {
        Duration configured = properties.getTimeout();
        return configured == null || configured.isZero() || configured.isNegative()
                ? DEFAULT_CLAIM_TIMEOUT : configured;
    }

    private String newClaimToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void requireCreatedInterview(CreateInterviewVO interview, String message) {
        if (interview == null || interview.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, message);
        }
    }

    private BusinessException creationInProgress() {
        return new BusinessException(
                ErrorCode.RESOURCE_RELATION_CONFLICT, "CREATION_IN_PROGRESS");
    }

    private BusinessException claimLost() {
        return new BusinessException(
                ErrorCode.RESOURCE_RELATION_CONFLICT, "CREATION_CLAIM_LOST");
    }

    public record ReplayClaim(InterviewReplay replay, String claimToken, boolean owner) {
    }

    public record RemediationClaim(
            InterviewRemediation remediation, String claimToken, boolean owner) {
    }

    public record ReplayCreation(InterviewReplay replay, CreateInterviewVO interview) {
    }

    public record RemediationCreation(
            InterviewRemediation remediation, CreateInterviewVO interview) {
    }
}
