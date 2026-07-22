package com.codecoachai.ai.agent.campaigncockpit;

import com.codecoachai.ai.agent.campaigncockpit.CampaignActionDecisionModels.Request;
import com.codecoachai.ai.agent.campaigncockpit.CampaignActionDecisionModels.View;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceEnvelope;
import com.codecoachai.ai.agent.campaigncockpit.domain.entity.CampaignActionDecision;
import com.codecoachai.ai.agent.campaigncockpit.mapper.CampaignActionDecisionMapper;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.ai.agent.service.support.AgentBusinessTimeProvider;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CampaignActionDecisionServiceImpl implements CampaignActionDecisionService {

    private final CampaignCockpitEvidenceClient evidenceClient;
    private final CampaignCockpitRuleEngine ruleEngine;
    private final CampaignActionDecisionMapper decisionMapper;
    private final AgentBusinessTimeProvider timeProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public View decide(Long userId, Long campaignId, Request request) {
        requireIds(userId, campaignId);
        validate(request);
        String status = request.getDecisionStatus().trim().toUpperCase(Locale.ROOT);
        String idempotencyHash = AgentAdaptivePlanHashUtils.sha256(
                request.getIdempotencyKey().trim());
        String payloadHash = AgentAdaptivePlanHashUtils.sha256(
                request.getSemanticKey().trim() + "|" + request.getSourceHash().trim() + "|"
                        + status + "|" + Objects.toString(request.getSnoozedUntil(), "")
                        + "|" + Objects.toString(request.getReason(), ""));
        CampaignActionDecision replay = decisionMapper.selectByIdempotency(
                userId, idempotencyHash);
        if (replay != null) {
            if (!Objects.equals(replay.getPayloadHash(), payloadHash)) {
                throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "同一幂等键不能用于不同的行动决策。");
            }
            return toView(replay);
        }
        ActionItem action = rawActions(userId, campaignId).stream()
                .filter(item -> Objects.equals(item.getSemanticKey(), request.getSemanticKey())
                        && Objects.equals(item.getSourceHash(), request.getSourceHash()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.STALE_SOURCE_VERSION, "行动来源已经变化，请刷新后重试。"));

        decisionMapper.deactivateCurrent(
                userId, campaignId, action.getSemanticKey(), action.getSourceHash());
        CampaignActionDecision decision = new CampaignActionDecision();
        decision.setUserId(userId);
        decision.setCampaignId(campaignId);
        decision.setSemanticKey(action.getSemanticKey());
        decision.setSourceHash(action.getSourceHash());
        decision.setActionType(action.getActionType());
        decision.setDecisionStatus(status);
        decision.setSnoozedUntil(request.getSnoozedUntil());
        decision.setReason(request.getReason());
        decision.setIdempotencyKeyHash(idempotencyHash);
        decision.setPayloadHash(payloadHash);
        decision.setDecidedAt(timeProvider.now());
        decision.setActiveGuard(1);
        decision.setDeleted(0);
        try {
            decisionMapper.insert(decision);
        } catch (DuplicateKeyException ex) {
            CampaignActionDecision concurrent = decisionMapper.selectByIdempotency(
                    userId, idempotencyHash);
            if (concurrent != null && Objects.equals(concurrent.getPayloadHash(), payloadHash)) {
                return toView(concurrent);
            }
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "行动决策发生并发冲突，请刷新后重试。");
        }
        return toView(decision);
    }

    @Override
    public List<View> list(Long userId, Long campaignId) {
        requireIds(userId, campaignId);
        verifyOwner(userId, campaignId);
        return decisionMapper.selectByCampaign(userId, campaignId).stream()
                .map(this::toView)
                .toList();
    }

    private List<ActionItem> rawActions(Long userId, Long campaignId) {
        EvidenceEnvelope evidence = verifyOwner(userId, campaignId);
        return ruleEngine.aggregate(evidence, timeProvider.now()).getActionQueue();
    }

    private EvidenceEnvelope verifyOwner(Long userId, Long campaignId) {
        try {
            Result<EvidenceEnvelope> response = evidenceClient.get(
                    userId, campaignId, timeProvider.now(), 100, 100);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "周期事实证据暂不可用。");
            }
            EvidenceEnvelope evidence = response.getData();
            if (!Objects.equals(userId, evidence.getUserId())
                    || !Objects.equals(campaignId, evidence.getCampaignId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "周期事实归属校验失败。");
            }
            return evidence;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "周期事实证据暂不可用。");
        }
    }

    private void validate(Request request) {
        if (request == null || !StringUtils.hasText(request.getSemanticKey())
                || !StringUtils.hasText(request.getSourceHash())
                || !StringUtils.hasText(request.getDecisionStatus())
                || !StringUtils.hasText(request.getIdempotencyKey())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行动决策请求不完整。");
        }
        String status = request.getDecisionStatus().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SNOOZED", "DISMISSED", "REOPENED", "PLAN_PREVIEWED").contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的行动决策状态。");
        }
        if ("SNOOZED".equals(status)
                && (request.getSnoozedUntil() == null
                || !request.getSnoozedUntil().isAfter(timeProvider.now()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "稍后处理必须设置未来时间。");
        }
    }

    private View toView(CampaignActionDecision source) {
        View result = new View();
        result.setId(source.getId());
        result.setCampaignId(source.getCampaignId());
        result.setSemanticKey(source.getSemanticKey());
        result.setSourceHash(source.getSourceHash());
        result.setActionType(source.getActionType());
        result.setDecisionStatus(source.getDecisionStatus());
        result.setSnoozedUntil(source.getSnoozedUntil());
        result.setReason(source.getReason());
        result.setDecidedAt(source.getDecidedAt());
        return result;
    }

    private void requireIds(Long userId, Long campaignId) {
        if (userId == null || campaignId == null || userId <= 0 || campaignId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户和周期不能为空。");
        }
    }
}
