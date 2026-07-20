package com.codecoachai.ai.agent.campaignreview.service;

import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReview;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewMemoryCandidate;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSnapshot;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSource;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;
import com.codecoachai.ai.agent.domain.entity.AgentMemory;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewMapper;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewMemoryCandidateMapper;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewSnapshotMapper;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewSourceMapper;
import com.codecoachai.ai.agent.mapper.AgentMemoryMapper;
import com.codecoachai.ai.agent.campaignreview.CareerCampaignReviewAiScene;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CareerCampaignReviewPersistenceServiceImpl
        implements CareerCampaignReviewPersistenceService {

    private static final int CLAIM_TIMEOUT_MINUTES = 5;

    private final CareerCampaignReviewMapper reviewMapper;
    private final CareerCampaignReviewSnapshotMapper snapshotMapper;
    private final CareerCampaignReviewSourceMapper sourceMapper;
    private final CareerCampaignReviewMemoryCandidateMapper candidateMapper;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentMemoryMapper agentMemoryMapper;

    @Override
    public CareerCampaignReview findOwned(Long userId, Long reviewId) {
        return reviewMapper.selectOwned(userId, reviewId);
    }

    @Override
    public CareerCampaignReview findOwnedByCampaign(Long userId, Long campaignId) {
        return reviewMapper.selectOwnedByCampaign(userId, campaignId);
    }

    @Override
    public CareerCampaignReviewSnapshot currentSnapshot(Long userId, CareerCampaignReview review) {
        return review == null || review.getCurrentSnapshotId() == null
                ? null
                : snapshotMapper.selectOwned(userId, review.getId(), review.getCurrentSnapshotId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Replay findIdempotentReplay(Long userId, Long campaignId,
                                       String idempotencyKeyHash, String payloadHash) {
        reviewMapper.ensureIdentity(userId, campaignId);
        CareerCampaignReview review = reviewMapper.selectIdentityForUpdate(userId, campaignId);
        if (review == null) {
            return null;
        }
        CareerCampaignReviewSnapshot snapshot = snapshotMapper.selectByIdempotency(
                userId, review.getId(), idempotencyKeyHash);
        if (snapshot != null && !payloadHash.equals(snapshot.getIdempotencyPayloadHash())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "同一幂等键不能用于不同的周期复盘请求");
        }
        return snapshot == null ? null : new Replay(review, snapshot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerationClaim claimGeneration(Long userId,
                                           Long campaignId,
                                           String generationFingerprint,
                                           String idempotencyKeyHash,
                                           String payloadHash) {
        reviewMapper.ensureIdentity(userId, campaignId);
        CareerCampaignReview review = reviewMapper.selectIdentityForUpdate(userId, campaignId);
        if (review == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "周期复盘根记录不存在");
        }
        CareerCampaignReviewSnapshot replay = snapshotMapper.selectByIdempotency(
                userId, review.getId(), idempotencyKeyHash);
        if (replay != null) {
            return new GenerationClaim(review, replay, null,
                    replay.getSnapshotVersion(), false);
        }
        LocalDateTime now = LocalDateTime.now();
        String token = UUID.randomUUID().toString();
        int updated = reviewMapper.claimGeneration(
                userId, review.getId(), generationFingerprint, token,
                idempotencyKeyHash, payloadHash, now,
                now.minusMinutes(CLAIM_TIMEOUT_MINUTES));
        if (updated != 1) {
            return new GenerationClaim(review, null, null,
                    safeVersion(review.getSnapshotVersion()) + 1, false);
        }
        return new GenerationClaim(review, null, token,
                safeVersion(review.getSnapshotVersion()) + 1, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerCampaignReviewSnapshot saveClaimed(
            Long userId,
            CareerCampaignReview review,
            String claimToken,
            String idempotencyKeyHash,
            String payloadHash,
            CareerCampaignReviewVO result,
            String inputHash,
            String requestId,
            List<CareerCampaignReviewSource> sources) {
        CareerCampaignReview locked = reviewMapper.selectIdentityForUpdate(
                userId, review.getCampaignId());
        if (locked == null || !claimToken.equals(locked.getGenerationClaimToken())) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "周期复盘生成 claim 已失效");
        }
        int nextVersion = safeVersion(locked.getSnapshotVersion()) + 1;
        CareerCampaignReviewSnapshot snapshot = new CareerCampaignReviewSnapshot();
        snapshot.setUserId(userId);
        snapshot.setReviewId(locked.getId());
        snapshot.setCampaignId(locked.getCampaignId());
        snapshot.setSnapshotVersion(nextVersion);
        snapshot.setDataCutoffAt(result.getDataCutoffAt());
        snapshot.setInputHash(inputHash);
        snapshot.setGenerationFingerprint(locked.getGenerationClaimFingerprint());
        snapshot.setIdempotencyKeyHash(idempotencyKeyHash);
        snapshot.setIdempotencyPayloadHash(payloadHash);
        snapshot.setSummary(result.getSummary());
        snapshot.setConfidenceLevel(result.getConfidenceLevel());
        snapshot.setFactsJson(write(result.getFacts()));
        snapshot.setCoverageJson(write(result.getCoverage()));
        snapshot.setLimitsJson(write(result.getLimits()));
        snapshot.setSignalsJson(write(result.getSignals()));
        snapshot.setMemoryCandidatesJson(write(result.getMemoryCandidates()));
        snapshot.setExperimentCandidatesJson(write(result.getExperimentCandidates()));
        snapshot.setNextCycleActionsJson(write(result.getNextCycleActions()));
        snapshot.setResultSource(Boolean.TRUE.equals(result.getFallback()) ? "FALLBACK" : "AI");
        snapshot.setFallback(Boolean.TRUE.equals(result.getFallback()) ? 1 : 0);
        snapshot.setFallbackReason(result.getFallbackReason());
        try {
            snapshotMapper.insertSnapshot(snapshot);
        } catch (DuplicateKeyException ex) {
            CareerCampaignReviewSnapshot replay = snapshotMapper.selectByIdempotency(
                    userId, locked.getId(), idempotencyKeyHash);
            if (replay != null) {
                return replay;
            }
            throw ex;
        }
        if (sources != null) {
            for (CareerCampaignReviewSource source : sources) {
                source.setUserId(userId);
                source.setSnapshotId(snapshot.getId());
                sourceMapper.insertSource(source);
            }
        }
        for (CareerCampaignReviewVO.Seed seed : result.getMemoryCandidates()) {
            CareerCampaignReviewMemoryCandidate candidate = new CareerCampaignReviewMemoryCandidate();
            candidate.setUserId(userId);
            candidate.setReviewId(locked.getId());
            candidate.setSnapshotId(snapshot.getId());
            candidate.setCandidateKey(seed.getSemanticKey());
            candidate.setSemanticHash(AgentAdaptivePlanHashUtils.sha256(
                    seed.getTitle() + "|" + seed.getDescription()));
            candidate.setTitle(seed.getTitle());
            candidate.setContent(seed.getDescription());
            candidate.setSourceRef(seed.getSourceRef());
            candidate.setConfidenceLevel(seed.getConfidenceLevel());
            candidate.setValidityDays(seed.getValidityDays());
            candidate.setExpiresAt(seed.getValidityDays() == null ? null
                    : LocalDateTime.now().plusDays(seed.getValidityDays()));
            candidateMapper.insertCandidate(candidate);
        }
        int published = reviewMapper.publishSnapshot(
                userId, locked.getId(), snapshot.getId(), nextVersion,
                result.getReportStatus(), claimToken);
        if (published != 1) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "周期复盘 snapshot 发布失败");
        }
        return snapshot;
    }

    @Override
    public void releaseClaim(Long userId, Long reviewId, String claimToken) {
        if (claimToken != null) {
            reviewMapper.releaseClaim(userId, reviewId, claimToken);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerCampaignReviewMemoryCandidate confirmCandidate(
            Long userId, Long candidateId, String idempotencyKeyHash, boolean confirmed) {
        CareerCampaignReviewMemoryCandidate candidate =
                candidateMapper.selectOwnedForUpdate(userId, candidateId);
        if (candidate == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "记忆候选不存在");
        }
        String requestedStatus = confirmed ? "CONFIRMED" : "REJECTED";
        if (!"PENDING".equals(candidate.getStatus())) {
            if (requestedStatus.equals(candidate.getStatus())) {
                return candidate;
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Memory candidate was already decided as " + candidate.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = candidateMapper.decide(
                userId, candidateId, requestedStatus,
                idempotencyKeyHash, confirmed ? now : null);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "记忆候选状态已变化");
        }
        candidate.setStatus(requestedStatus);
        candidate.setConfirmedAt(confirmed ? now : null);
        candidate.setDecisionIdempotencyKeyHash(idempotencyKeyHash);
        if (confirmed) {
            publishConfirmedMemory(candidate);
        }
        return candidate;
    }

    private void publishConfirmedMemory(CareerCampaignReviewMemoryCandidate candidate) {
        if (agentMemoryMapper == null || candidate == null || candidate.getId() == null) {
            return;
        }
        AgentMemory existing = agentMemoryMapper.selectOne(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getUserId, candidate.getUserId())
                .eq(AgentMemory::getSourceType, "USER_CONFIRMED_CAREER_CAMPAIGN_REVIEW")
                .eq(AgentMemory::getSourceId, candidate.getId())
                .eq(AgentMemory::getDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        AgentMemory memory = new AgentMemory();
        memory.setUserId(candidate.getUserId());
        memory.setMemoryType("CAREER_LEARNING");
        memory.setContent(candidate.getContent());
        memory.setSourceType("USER_CONFIRMED_CAREER_CAMPAIGN_REVIEW");
        memory.setSourceId(candidate.getId());
        memory.setConfidence(confidence(candidate.getConfidenceLevel()));
        memory.setEnabled(1);
        memory.setDeleted(0);
        agentMemoryMapper.insert(memory);
    }

    private BigDecimal confidence(String value) {
        return switch (value == null ? "" : value.trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> new BigDecimal("0.90");
            case "LOW" -> new BigDecimal("0.40");
            default -> new BigDecimal("0.70");
        };
    }

    private int safeVersion(Integer version) {
        return version == null ? 0 : Math.max(version, 0);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("周期复盘 JSON 序列化失败", ex);
        }
    }

    private String markdown(CareerCampaignReviewVO result) {
        return "# 周期复盘\n\n"
                + "## 摘要\n" + result.getSummary() + "\n\n"
                + "## 事实\n" + result.getFacts().size() + " 项\n\n"
                + "## 下一周期动作\n" + result.getNextCycleActions().size() + " 项\n";
    }
}
