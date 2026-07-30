package com.codecoachai.ai.agent.campaignpulse;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CockpitView;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitService;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Computation;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.GenerateRequest;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.HistoryView;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Narrative;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.PlanPreviewRequest;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.PulseView;
import com.codecoachai.ai.agent.campaignpulse.domain.entity.CampaignPulseSnapshot;
import com.codecoachai.ai.agent.campaignpulse.domain.entity.CampaignPulseSource;
import com.codecoachai.ai.agent.campaignpulse.mapper.CampaignPulseSnapshotMapper;
import com.codecoachai.ai.agent.campaignpulse.mapper.CampaignPulseSourceMapper;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangePreviewVO;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CampaignPulseServiceImpl implements CampaignPulseService {

    private static final String GENERATION_VERSION = "V8_CAMPAIGN_PULSE_V1";

    private final CampaignCockpitService cockpitService;
    private final CampaignPulseRuleEngine ruleEngine;
    private final CampaignPulseNarrativeEnhancer narrativeEnhancer;
    private final CampaignPulsePersistenceService persistenceService;
    private final CampaignPulseSnapshotMapper snapshotMapper;
    private final CampaignPulseSourceMapper sourceMapper;
    private final CampaignPulseJsonCodec jsonCodec;
    private final CampaignPulsePlanPreviewAdapter planPreviewAdapter;

    @Override
    public PulseView generate(Long userId, GenerateRequest request) {
        requireUser(userId);
        if (request == null || request.getCampaignId() == null
                || !StringUtils.hasText(request.getIdempotencyKey())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "周期和幂等键不能为空。");
        }
        Long campaignId = request.getCampaignId();
        CockpitView cockpit = cockpitService.get(userId, campaignId);
        requireGeneratableStatus(cockpit);
        PulseView previous = nullableCurrent(userId, campaignId);
        Computation computation = ruleEngine.compute(cockpit, previous);
        String fingerprint = AgentAdaptivePlanHashUtils.sha256(
                GENERATION_VERSION + "|" + computation.getInputHash());
        String idempotencyKeyHash = AgentAdaptivePlanHashUtils.sha256(
                request.getIdempotencyKey().trim());
        String idempotencyPayloadHash = AgentAdaptivePlanHashUtils.sha256(
                campaignId + "|" + computation.getInputHash() + "|"
                        + Objects.toString(request.getRequestId(), ""));
        CampaignPulseSnapshot idempotent = snapshotMapper.selectByIdempotency(
                userId, idempotencyKeyHash);
        if (idempotent != null) {
            if (!Objects.equals(idempotent.getIdempotencyPayloadHash(), idempotencyPayloadHash)) {
                throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "同一幂等键不能用于不同的周期脉搏请求。");
            }
            return toView(userId, idempotent);
        }

        CampaignPulsePersistenceService.Claim claim =
                persistenceService.claim(userId, campaignId, fingerprint);
        if (claim.replay() != null) {
            return toView(userId, claim.replay());
        }
        if (!claim.owner()) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "该周期脉搏正在生成，请稍后刷新。");
        }
        try {
            Narrative narrative = narrativeEnhancer.enhance(userId, campaignId, computation);
            CampaignPulseSnapshot saved = persistenceService.save(
                    userId, claim, fingerprint, idempotencyKeyHash,
                    idempotencyPayloadHash, computation, narrative);
            return toView(userId, saved);
        } catch (RuntimeException ex) {
            persistenceService.release(userId, claim);
            throw ex;
        }
    }

    @Override
    public PulseView current(Long userId, Long campaignId) {
        requireUser(userId);
        requireCampaign(campaignId);
        cockpitService.get(userId, campaignId);
        CampaignPulseSnapshot snapshot = snapshotMapper.selectCurrent(userId, campaignId);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该周期尚未生成脉搏。");
        }
        return toView(userId, snapshot);
    }

    @Override
    public HistoryView history(Long userId, Long campaignId) {
        requireUser(userId);
        requireCampaign(campaignId);
        cockpitService.get(userId, campaignId);
        HistoryView result = new HistoryView();
        result.setCampaignId(campaignId);
        result.setSnapshots(snapshotMapper.selectHistory(userId, campaignId, 50).stream()
                .map(snapshot -> toView(userId, snapshot))
                .toList());
        return result;
    }

    @Override
    public AgentPlanChangePreviewVO previewPlan(
            Long userId, Long snapshotId, PlanPreviewRequest request) {
        requireUser(userId);
        if (snapshotId == null || request == null
                || !StringUtils.hasText(request.getIdempotencyKey())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "快照和幂等键不能为空。");
        }
        PulseView pulse = snapshot(userId, snapshotId);
        String status = Objects.toString(pulse.getFacts().get("campaignStatus"), "")
                .toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "只有活动周期可以将脉搏行动加入计划。");
        }
        return planPreviewAdapter.preview(userId, pulse, request);
    }

    @Override
    public PulseView snapshot(Long userId, Long snapshotId) {
        requireUser(userId);
        if (snapshotId == null || snapshotId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "快照不能为空。");
        }
        CampaignPulseSnapshot snapshot = snapshotMapper.selectOwned(userId, snapshotId);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "周期脉搏快照不存在。");
        }
        return toView(userId, snapshot);
    }

    private PulseView nullableCurrent(Long userId, Long campaignId) {
        CampaignPulseSnapshot snapshot = snapshotMapper.selectCurrent(userId, campaignId);
        return snapshot == null ? null : toView(userId, snapshot);
    }

    private PulseView toView(Long userId, CampaignPulseSnapshot snapshot) {
        PulseView result = new PulseView();
        result.setPulseId(snapshot.getPulseId());
        result.setSnapshotId(snapshot.getId());
        result.setCampaignId(snapshot.getCampaignId());
        result.setSnapshotVersion(snapshot.getSnapshotVersion());
        result.setDataCutoffAt(snapshot.getDataCutoffAt());
        result.setInputHash(snapshot.getInputHash());
        result.setFacts(jsonCodec.read(snapshot.getFactsJson(),
                new TypeReference<Map<String, Object>>() { }, new LinkedHashMap<>()));
        result.setMetrics(jsonCodec.read(snapshot.getMetricsJson(),
                new TypeReference<Map<String, Object>>() { }, new LinkedHashMap<>()));
        result.setChanges(jsonCodec.read(snapshot.getChangesJson(),
                new TypeReference<List<String>>() { }, new ArrayList<>()));
        result.setDriftSignals(jsonCodec.read(snapshot.getDriftSignalsJson(),
                new TypeReference<List<String>>() { }, new ArrayList<>()));
        result.setLimits(jsonCodec.read(snapshot.getLimitsJson(),
                new TypeReference<List<String>>() { }, new ArrayList<>()));
        result.setActionSeeds(jsonCodec.read(snapshot.getActionSeedsJson(),
                new TypeReference<List<com.codecoachai.ai.agent.campaigncockpit
                        .CampaignCockpitModels.ActionItem>>() { }, new ArrayList<>()));
        result.setNarrative(jsonCodec.read(snapshot.getNarrativeJson(),
                CampaignPulseModels.Narrative.class, new CampaignPulseModels.Narrative()));
        result.setSources(sourceMapper.selectBySnapshot(userId, snapshot.getId()).stream()
                .map(this::toEvidenceRef)
                .toList());
        result.setConfidenceLevel(snapshot.getConfidenceLevel());
        result.setFallback(Boolean.TRUE.equals(snapshot.getFallback()));
        result.setAiCallLogId(snapshot.getAiCallLogId());
        result.setCreatedAt(snapshot.getCreatedAt());
        return result;
    }

    private com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceRef
            toEvidenceRef(CampaignPulseSource source) {
        var result = new com.codecoachai.ai.agent.campaigncockpit
                .CampaignCockpitModels.EvidenceRef();
        result.setSourceType(source.getSourceType());
        result.setSourceId(source.getSourceId());
        result.setSourceVersion(source.getSourceVersion());
        result.setSourceHash(source.getSourceHash());
        result.setApplicationId(source.getApplicationId());
        result.setCampaignId(source.getCampaignId());
        result.setObservedAt(source.getObservedAt());
        result.setFieldPath(source.getFieldPath());
        result.setSummary(source.getSafeSummary());
        return result;
    }

    private void requireGeneratableStatus(CockpitView cockpit) {
        String status = cockpit.getCampaign() == null
                ? "" : Objects.toString(cockpit.getCampaign().getStatus(), "");
        if (!List.of("ACTIVE", "PAUSED", "COMPLETED").contains(
                status.toUpperCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "当前周期状态不允许生成周期脉搏。");
        }
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不能为空。");
        }
    }

    private void requireCampaign(Long campaignId) {
        if (campaignId == null || campaignId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "周期不能为空。");
        }
    }
}
