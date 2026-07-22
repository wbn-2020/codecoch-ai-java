package com.codecoachai.ai.agent.campaignreview.service;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignMemoryCandidateConfirmDTO;
import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.CareerCampaignReviewEvidenceEnvelope;
import com.codecoachai.ai.agent.campaignreview.CareerCampaignReviewVersions;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReview;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewMemoryCandidate;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSnapshot;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSource;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewMemoryCandidateMapper;
import com.codecoachai.ai.agent.feign.CareerCampaignReviewEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.CareerCampaignReviewEvidenceVO;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.core.domain.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerCampaignReviewServiceImpl implements CareerCampaignReviewService {

    private final CareerCampaignReviewAiService aiService;
    private final CareerCampaignReviewPersistenceService persistenceService;
    private final CareerCampaignReviewMemoryCandidateMapper candidateMapper;
    private final ObjectMapper objectMapper;
    private final CareerCampaignReviewEvidenceFeignClient evidenceClient;

    @Override
    public CareerCampaignReviewVO generate(Long userId, CareerCampaignReviewGenerateDTO request) {
        CareerCampaignReviewEvidenceEnvelope evidence = enrichFromResume(userId, request);
        CareerCampaignReviewGenerateDTO actualRequest = evidence.trustedRequest(request);
        validateRequest(actualRequest);
        String idempotencyKeyHash = AgentAdaptivePlanHashUtils.sha256(actualRequest.getIdempotencyKey().trim());
        String payloadHash = evidence.payloadHash();
        CareerCampaignReviewPersistenceService.Replay replay =
                persistenceService.findIdempotentReplay(
                        userId, actualRequest.getCampaignId(), idempotencyKeyHash, payloadHash);
        if (replay != null) {
            return toVO(replay.review(), replay.snapshot(), userId);
        }
        String generationFingerprint = evidence.generationFingerprint();
        CareerCampaignReviewPersistenceService.GenerationClaim claim =
                persistenceService.claimGeneration(userId, actualRequest.getCampaignId(),
                        generationFingerprint, idempotencyKeyHash, payloadHash);
        if (claim.replay() != null) {
            return toVO(claim.review(), claim.replay(), userId);
        }
        if (!claim.owner()) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "周期复盘正在生成，请稍后重试");
        }
        boolean saved = false;
        try {
            CareerCampaignReviewVO result = aiService.generate(actualRequest);
            CareerCampaignReviewSnapshot snapshot = persistenceService.saveClaimed(
                    userId,
                    claim.review(),
                    claim.claimToken(),
                    idempotencyKeyHash,
                    payloadHash,
                    result,
                    evidence.getInputHash(),
                    actualRequest.getRequestId(),
                    evidence.manifestJson(),
                    CareerCampaignReviewVersions.EVIDENCE_SCHEMA_VERSION,
                    CareerCampaignReviewVersions.RULE_VERSION,
                    sources(userId, evidence));
            saved = true;
            return toVO(claim.review(), snapshot, userId);
        } finally {
            if (!saved) {
                persistenceService.releaseClaim(userId, claim.review().getId(), claim.claimToken());
            }
        }
    }

    private CareerCampaignReviewEvidenceEnvelope enrichFromResume(
            Long userId, CareerCampaignReviewGenerateDTO request) {
        if (request == null || request.getCampaignId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "campaignId 不能为空");
        }
        if (evidenceClient == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "周期事实证据不可用，暂时无法生成复盘");
        }
        try {
            Result<CareerCampaignReviewEvidenceVO> response = evidenceClient.get(
                    userId, request.getCampaignId(), LocalDateTime.now());
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "周期事实证据校验失败，暂时无法生成复盘");
            }
            CareerCampaignReviewEvidenceVO evidence = response.getData();
            if (!userId.equals(evidence.getUserId())
                    || !request.getCampaignId().equals(evidence.getCampaignId())) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "周期事实证据与当前用户或周期不匹配");
            }
            return CareerCampaignReviewEvidenceEnvelope.from(evidence, LocalDateTime.now());
        } catch (RuntimeException ex) {
            log.warn("Campaign review evidence lookup failed; generation is blocked campaignId={}",
                    request.getCampaignId(), ex);
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "周期事实证据校验失败，暂时无法生成复盘");
        }
    }

    @Override
    public CareerCampaignReviewVO detail(Long userId, Long reviewId) {
        CareerCampaignReview review = persistenceService.findOwned(userId, reviewId);
        return detailForReview(userId, review);
    }

    @Override
    public CareerCampaignReviewVO detailByCampaign(Long userId, Long campaignId) {
        CareerCampaignReview review = persistenceService.findOwnedByCampaign(userId, campaignId);
        return detailForReview(userId, review);
    }

    private CareerCampaignReviewVO detailForReview(Long userId, CareerCampaignReview review) {
        if (review == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "周期复盘不存在");
        }
        CareerCampaignReviewSnapshot snapshot = persistenceService.currentSnapshot(userId, review);
        if (snapshot == null) {
            CareerCampaignReviewVO result = new CareerCampaignReviewVO();
            result.setReviewId(review.getId());
            result.setCampaignId(review.getCampaignId());
            result.setReportStatus(review.getReviewStatus());
            result.setSnapshotVersion(review.getSnapshotVersion());
            return result;
        }
        return toVO(review, snapshot, userId);
    }

    @Override
    public CareerCampaignReviewVO confirmMemoryCandidate(
            Long userId, Long candidateId, CareerCampaignMemoryCandidateConfirmDTO request) {
        if (request == null || request.getIdempotencyKey() == null
                || request.getIdempotencyKey().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "候选确认幂等键不能为空");
        }
        CareerCampaignReviewMemoryCandidate candidate = persistenceService.confirmCandidate(
                userId, candidateId,
                AgentAdaptivePlanHashUtils.sha256(request.getIdempotencyKey().trim()),
                Boolean.TRUE.equals(request.getConfirmed()));
        CareerCampaignReview review = persistenceService.findOwned(
                userId, candidate.getReviewId());
        CareerCampaignReviewSnapshot snapshot = persistenceService.currentSnapshot(userId, review);
        return toVO(review, snapshot, userId);
    }

    private void validateRequest(CareerCampaignReviewGenerateDTO request) {
        if (request == null || request.getCampaignId() == null
                || request.getIdempotencyKey() == null
                || request.getIdempotencyKey().trim().isEmpty()
                || request.getDataCutoffAt() == null
                || !Boolean.TRUE.equals(request.getCompleted())
                || !Boolean.TRUE.equals(request.getAllOpportunitiesClosed())
                || !"COMPLETED".equalsIgnoreCase(request.getCampaignStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "只有 COMPLETED 且机会已关闭的周期可以生成最终复盘");
        }
    }

    private List<CareerCampaignReviewSource> sources(
            Long userId, CareerCampaignReviewEvidenceEnvelope evidence) {
        List<CareerCampaignReviewSource> result = new ArrayList<>();
        List<CareerCampaignReviewGenerateDTO.Source> values = evidence.getSources();
        if (values == null) {
            return result;
        }
        for (CareerCampaignReviewGenerateDTO.Source value : values) {
            CareerCampaignReviewSource source = new CareerCampaignReviewSource();
            source.setUserId(userId);
            source.setSourceType(value.getSourceType());
            source.setSourceId(value.getSourceId());
            source.setSourceTime(value.getSourceTime());
            source.setSourceUpdatedAt(value.getSourceUpdatedAt());
            source.setSourceHash(value.getSourceHash());
            source.setInclusionStatus("INCLUDED");
            source.setMetadataJson(evidence.sourceMetadataJson(value.getSourceVersion()));
            result.add(source);
        }
        return result;
    }

    private CareerCampaignReviewVO toVO(
            CareerCampaignReview review, CareerCampaignReviewSnapshot snapshot, Long userId) {
        CareerCampaignReviewVO result = new CareerCampaignReviewVO();
        result.setReviewId(review.getId());
        result.setSnapshotId(snapshot.getId());
        result.setCampaignId(review.getCampaignId());
        result.setSnapshotVersion(snapshot.getSnapshotVersion());
        result.setReportStatus(review.getReviewStatus());
        result.setConfidenceLevel(snapshot.getConfidenceLevel());
        result.setFallback(snapshot.getFallback() != null && snapshot.getFallback() == 1);
        result.setFallbackReason(snapshot.getFallbackReason());
        result.setSummary(snapshot.getSummary());
        result.setDataCutoffAt(snapshot.getDataCutoffAt());
        result.setFacts(read(snapshot.getFactsJson(), new TypeReference<>() {
        }));
        result.setCoverage(read(snapshot.getCoverageJson(), new TypeReference<>() {
        }));
        result.setLimits(read(snapshot.getLimitsJson(), new TypeReference<>() {
        }));
        result.setSignals(read(snapshot.getSignalsJson(), new TypeReference<>() {
        }));
        result.setMemoryCandidates(read(snapshot.getMemoryCandidatesJson(), new TypeReference<>() {
        }));
        result.setExperimentCandidates(read(snapshot.getExperimentCandidatesJson(), new TypeReference<>() {
        }));
        result.setNextCycleActions(read(snapshot.getNextCycleActionsJson(), new TypeReference<>() {
        }));
        for (CareerCampaignReviewMemoryCandidate candidate :
                candidateMapper.selectBySnapshot(userId, snapshot.getId())) {
            result.getMemoryCandidates().stream()
                    .filter(seed -> AgentAdaptivePlanHashUtils.sha256(
                            seed.getTitle() + "|" + seed.getDescription())
                    .equals(candidate.getSemanticHash()))
                    .findFirst()
                    .ifPresent(seed -> {
                        seed.setCandidateId(candidate.getId());
                        seed.setStatus(candidate.getStatus());
                        seed.setEffective("CONFIRMED".equals(candidate.getStatus())
                                && (candidate.getExpiresAt() == null
                                || candidate.getExpiresAt().isAfter(LocalDateTime.now())));
                    });
        }
        return result;
    }

    private <T> List<T> read(String value, TypeReference<List<T>> type) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            return new ArrayList<>();
        }
    }
}
