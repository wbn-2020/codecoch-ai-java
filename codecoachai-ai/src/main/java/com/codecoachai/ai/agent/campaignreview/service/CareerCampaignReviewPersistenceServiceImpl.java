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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            String evidenceManifestJson,
            String evidenceSchemaVersion,
            String ruleVersion,
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
        snapshot.setEvidenceManifestJson(evidenceManifestJson);
        snapshot.setEvidenceSchemaVersion(evidenceSchemaVersion);
        snapshot.setRuleVersion(ruleVersion);
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
            candidate.setCandidateScopeType("CAMPAIGN");
            candidate.setCandidateScopeKey(String.valueOf(locked.getCampaignId()));
            candidate.setCandidateType("CAMPAIGN_REVIEW");
            candidate.setUsageSourceHash(inputHash);
            candidate.setEvidenceCount(result.getFacts() == null ? 0 : result.getFacts().size());
            candidate.setSampleCount(0);
            candidate.setLimitsJson(write(result.getLimits()));
            candidate.setCandidateKey(seed.getSemanticKey());
            candidate.setSemanticHash(AgentAdaptivePlanHashUtils.sha256(
                    seed.getTitle() + "|" + seed.getDescription()));
            candidate.setTitle(seed.getTitle());
            candidate.setContent(seed.getDescription());
            candidate.setSourceRef(seed.getSourceRef());
            candidate.setConfidenceLevel(seed.getConfidenceLevel());
            candidate.setStatus("PENDING_CONFIRMATION");
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
        if (!List.of("PENDING", "PENDING_CONFIRMATION").contains(candidate.getStatus())) {
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
            publishMemoryDraft(candidate);
        }
        return candidate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerCampaignReviewMemoryCandidate decideCandidate(
            Long userId, Long candidateId, String decisionCode,
            String idempotencyKeyHash, String payloadHash, String editedContent) {
        CareerCampaignReviewMemoryCandidate candidate =
                candidateMapper.selectOwnedForUpdate(userId, candidateId);
        if (candidate == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习候选不存在");
        }
        String code = decisionCode == null ? "" : decisionCode.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("KEEP", "EDIT", "CONTINUE", "REJECT").contains(code)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的候选决策");
        }
        if ("EDIT".equals(code) && (editedContent == null || editedContent.trim().isEmpty())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "EDIT 决策必须提供修改后的内容");
        }
        Map<String, DecisionHistoryEntry> decisionHistory =
                readDecisionHistory(candidate.getDecisionHistoryJson());
        DecisionHistoryEntry replay = decisionHistory.get(idempotencyKeyHash);
        if (replay != null) {
            if (payloadHash != null && payloadHash.equals(replay.payloadHash())
                    && code.equals(replay.decisionCode())) {
                return candidate;
            }
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "同一幂等键不能用于不同的候选决策");
        }
        if (candidate.getDecisionIdempotencyKeyHash() != null
                && candidate.getDecisionIdempotencyKeyHash().equals(idempotencyKeyHash)) {
            if (payloadHash != null && payloadHash.equals(candidate.getDecisionPayloadHash())
                    && code.equals(candidate.getDecisionCode())) {
                return candidate;
            }
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "同一幂等键不能用于不同的候选决策");
        }
        if (Set.of("CONFIRMED", "CONFIRMED_BY_USER", "REJECTED", "EXPIRED")
                .contains(candidate.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "候选已结束，不能再次决策");
        }
        LocalDateTime now = LocalDateTime.now();
        if (candidate.getExpiresAt() != null && !candidate.getExpiresAt().isAfter(now)) {
            candidateMapper.expire(userId, candidateId, now);
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "候选已过期");
        }
        String nextStatus = switch (code) {
            case "REJECT" -> "REJECTED";
            case "CONTINUE" -> "WEAK_OBSERVATION";
            default -> "CONFIRMED_BY_USER";
        };
        String content = "EDIT".equals(code) ? editedContent.trim() : candidate.getContent();
        LocalDateTime expiresAt = "CONTINUE".equals(code)
                ? now.plusDays(candidate.getValidityDays() == null
                || candidate.getValidityDays() < 1 ? 30 : candidate.getValidityDays())
                : candidate.getExpiresAt();
        decisionHistory.put(idempotencyKeyHash,
                new DecisionHistoryEntry(code, payloadHash, now.toString()));
        String decisionHistoryJson = write(decisionHistory);
        int updated = candidateMapper.decideV9(
                userId, candidateId, nextStatus, content, code, payloadHash,
                decisionHistoryJson, idempotencyKeyHash, now,
                "KEEP".equals(code) || "EDIT".equals(code) ? now : null,
                expiresAt);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "候选状态已变化");
        }
        candidate.setStatus(nextStatus);
        candidate.setContent(content);
        candidate.setDecisionCode(code);
        candidate.setDecisionPayloadHash(payloadHash);
        candidate.setDecisionHistoryJson(decisionHistoryJson);
        candidate.setDecisionIdempotencyKeyHash(idempotencyKeyHash);
        candidate.setDecisionAt(now);
        candidate.setConfirmedAt(
                "KEEP".equals(code) || "EDIT".equals(code) ? now : null);
        candidate.setExpiresAt(expiresAt);
        if ("KEEP".equals(code) || "EDIT".equals(code)) {
            publishMemoryDraft(candidate);
        }
        return candidate;
    }

    private void publishMemoryDraft(CareerCampaignReviewMemoryCandidate candidate) {
        if (agentMemoryMapper == null || candidate == null || candidate.getId() == null) {
            return;
        }
        String promotionKeyHash = AgentAdaptivePlanHashUtils.sha256(
                "v9-evidence-learning|" + candidate.getUserId() + "|" + candidate.getId()
                        + "|" + candidate.getSemanticHash());
        AgentMemory existing = agentMemoryMapper.selectOne(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getUserId, candidate.getUserId())
                .eq(AgentMemory::getPromotionKeyHash, promotionKeyHash)
                .eq(AgentMemory::getDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            candidate.setPromotedMemoryId(existing.getId());
            return;
        }
        AgentMemory memory = new AgentMemory();
        memory.setUserId(candidate.getUserId());
        memory.setMemoryType("CAREER_LEARNING");
        memory.setContent(candidate.getContent());
        memory.setSourceType("EVIDENCE_LEARNING_CANDIDATE");
        memory.setSourceId(candidate.getId());
        memory.setPromotionKeyHash(promotionKeyHash);
        memory.setConfidence(confidence(candidate.getConfidenceLevel()));
        memory.setEnabled(0);
        memory.setDeleted(0);
        try {
            agentMemoryMapper.insert(memory);
        } catch (DuplicateKeyException ex) {
            existing = agentMemoryMapper.selectOne(new LambdaQueryWrapper<AgentMemory>()
                    .eq(AgentMemory::getUserId, candidate.getUserId())
                    .eq(AgentMemory::getPromotionKeyHash, promotionKeyHash)
                    .eq(AgentMemory::getDeleted, 0)
                    .last("LIMIT 1"));
            if (existing == null) {
                throw ex;
            }
            memory = existing;
        }
        candidate.setPromotedMemoryId(memory.getId());
        if (candidate.getId() != null && memory.getId() != null) {
            candidateMapper.updatePromotedMemory(
                    candidate.getUserId(), candidate.getId(), memory.getId());
        }
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

    private Map<String, DecisionHistoryEntry> readDecisionHistory(String value) {
        Map<String, DecisionHistoryEntry> history = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return history;
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            if (!root.isObject()) {
                return history;
            }
            root.fields().forEachRemaining(entry -> {
                JsonNode item = entry.getValue();
                String decisionCode = item.path("decisionCode").asText(null);
                String payloadHash = item.path("payloadHash").asText(null);
                String decidedAt = decisionHistoryTimestamp(item.get("decidedAt"));
                if (decisionCode != null && payloadHash != null) {
                    history.put(entry.getKey(), new DecisionHistoryEntry(
                            decisionCode, payloadHash, decidedAt));
                }
            });
            return history;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "候选决策历史无法解析");
        }
    }

    private String decisionHistoryTimestamp(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        try {
            return objectMapper.treeToValue(value, LocalDateTime.class).toString();
        } catch (Exception exception) {
            return value.toString();
        }
    }

    private record DecisionHistoryEntry(
            String decisionCode, String payloadHash, String decidedAt) {
    }

    private String markdown(CareerCampaignReviewVO result) {
        return "# 周期复盘\n\n"
                + "## 摘要\n" + result.getSummary() + "\n\n"
                + "## 事实\n" + result.getFacts().size() + " 项\n\n"
                + "## 下一周期动作\n" + result.getNextCycleActions().size() + " 项\n";
    }
}
