package com.codecoachai.ai.agent.campaignpulse;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceRef;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Computation;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Narrative;
import com.codecoachai.ai.agent.campaignpulse.domain.entity.CampaignPulse;
import com.codecoachai.ai.agent.campaignpulse.domain.entity.CampaignPulseSnapshot;
import com.codecoachai.ai.agent.campaignpulse.domain.entity.CampaignPulseSource;
import com.codecoachai.ai.agent.campaignpulse.mapper.CampaignPulseMapper;
import com.codecoachai.ai.agent.campaignpulse.mapper.CampaignPulseSnapshotMapper;
import com.codecoachai.ai.agent.campaignpulse.mapper.CampaignPulseSourceMapper;
import com.codecoachai.ai.agent.service.support.AgentBusinessTimeProvider;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignPulsePersistenceService {

    private static final int CLAIM_TIMEOUT_MINUTES = 5;

    private final CampaignPulseMapper pulseMapper;
    private final CampaignPulseSnapshotMapper snapshotMapper;
    private final CampaignPulseSourceMapper sourceMapper;
    private final CampaignPulseJsonCodec jsonCodec;
    private final AgentBusinessTimeProvider timeProvider;

    @Transactional(rollbackFor = Exception.class)
    public Claim claim(Long userId, Long campaignId, String fingerprint) {
        pulseMapper.ensure(userId, campaignId);
        CampaignPulse pulse = pulseMapper.selectOwnedForUpdate(userId, campaignId);
        CampaignPulseSnapshot replay = snapshotMapper.selectByFingerprint(
                userId, campaignId, fingerprint);
        if (replay != null) {
            return new Claim(pulse, null, replay, false);
        }
        String token = UUID.randomUUID().toString();
        LocalDateTime now = timeProvider.now();
        int claimed = pulseMapper.claim(userId, pulse.getId(), token, fingerprint, now,
                now.minusMinutes(CLAIM_TIMEOUT_MINUTES));
        if (claimed == 0) {
            return new Claim(pulse, null, null, false);
        }
        return new Claim(pulse, token, null, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public CampaignPulseSnapshot save(
            Long userId,
            Claim claim,
            String fingerprint,
            String idempotencyKeyHash,
            String idempotencyPayloadHash,
            Computation computation,
            Narrative narrative) {
        CampaignPulse locked = pulseMapper.selectOwnedForUpdate(
                userId, claim.pulse().getCampaignId());
        if (locked == null || !claim.claimToken().equals(locked.getGenerationClaimToken())) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "周期脉搏生成权已失效。");
        }
        CampaignPulseSnapshot replay = snapshotMapper.selectByFingerprint(
                userId, locked.getCampaignId(), fingerprint);
        if (replay != null) {
            pulseMapper.release(userId, locked.getId(), claim.claimToken());
            return replay;
        }
        CampaignPulseSnapshot snapshot = new CampaignPulseSnapshot();
        snapshot.setUserId(userId);
        snapshot.setPulseId(locked.getId());
        snapshot.setCampaignId(locked.getCampaignId());
        snapshot.setSnapshotVersion((locked.getSnapshotVersion() == null
                ? 0 : locked.getSnapshotVersion()) + 1);
        snapshot.setDataCutoffAt(computation.getDataCutoffAt());
        snapshot.setInputHash(computation.getInputHash());
        snapshot.setGenerationFingerprint(fingerprint);
        snapshot.setIdempotencyKeyHash(idempotencyKeyHash);
        snapshot.setIdempotencyPayloadHash(idempotencyPayloadHash);
        snapshot.setFactsJson(jsonCodec.write(computation.getFacts()));
        snapshot.setMetricsJson(jsonCodec.write(computation.getMetrics()));
        snapshot.setChangesJson(jsonCodec.write(computation.getChanges()));
        snapshot.setDriftSignalsJson(jsonCodec.write(computation.getDriftSignals()));
        snapshot.setLimitsJson(jsonCodec.write(computation.getLimits()));
        snapshot.setActionSeedsJson(jsonCodec.write(computation.getActionSeeds()));
        snapshot.setNarrativeJson(jsonCodec.write(narrative));
        snapshot.setConfidenceLevel(computation.getConfidenceLevel());
        snapshot.setFallback(Boolean.TRUE.equals(narrative.getFallback()));
        snapshot.setAiCallLogId(narrative.getAiCallLogId());
        snapshot.setDeleted(0);
        try {
            snapshotMapper.insert(snapshot);
        } catch (DuplicateKeyException ex) {
            CampaignPulseSnapshot concurrent = snapshotMapper.selectByFingerprint(
                    userId, locked.getCampaignId(), fingerprint);
            if (concurrent != null) {
                pulseMapper.release(userId, locked.getId(), claim.claimToken());
                return concurrent;
            }
            throw ex;
        }
        for (EvidenceRef ref : computation.getSources()) {
            CampaignPulseSource source = new CampaignPulseSource();
            source.setUserId(userId);
            source.setSnapshotId(snapshot.getId());
            source.setSourceType(ref.getSourceType());
            source.setSourceId(ref.getSourceId());
            source.setSourceVersion(ref.getSourceVersion());
            source.setSourceHash(ref.getSourceHash());
            source.setApplicationId(ref.getApplicationId());
            source.setCampaignId(locked.getCampaignId());
            source.setObservedAt(ref.getObservedAt());
            source.setFieldPath(ref.getFieldPath());
            source.setSafeSummary(ref.getSummary());
            source.setDeleted(0);
            sourceMapper.insert(source);
        }
        int published = pulseMapper.publish(
                userId, locked.getId(), snapshot.getId(), snapshot.getSnapshotVersion(),
                timeProvider.now(), claim.claimToken());
        if (published != 1) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "周期脉搏发布发生并发冲突。");
        }
        return snapshot;
    }

    public void release(Long userId, Claim claim) {
        if (claim != null && claim.owner() && claim.claimToken() != null) {
            pulseMapper.release(userId, claim.pulse().getId(), claim.claimToken());
        }
    }

    public record Claim(
            CampaignPulse pulse,
            String claimToken,
            CampaignPulseSnapshot replay,
            boolean owner) {
    }
}
