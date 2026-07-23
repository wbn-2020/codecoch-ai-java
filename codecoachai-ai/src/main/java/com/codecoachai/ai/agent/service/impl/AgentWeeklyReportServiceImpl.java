package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportGenerateDTO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportQueryDTO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportRefreshDTO;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReport;
import com.codecoachai.ai.agent.domain.vo.weekly.AgentWeeklyReportVO;
import com.codecoachai.ai.agent.service.AgentWeeklyReportService;
import com.codecoachai.ai.agent.weekly.config.WeeklyReportFeatureProperties;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.AggregationResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.EvidenceBundle;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.GenerationClaim;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.NarrativeResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.QueryContext;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.RequestContext;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.SaveCommand;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.StoredView;
import com.codecoachai.ai.agent.weekly.service.AgentWeeklyReportPersistenceService;
import com.codecoachai.ai.agent.weekly.service.WeeklyReportAiEnhancer;
import com.codecoachai.ai.agent.weekly.service.WeeklyReportEvidenceCollector;
import com.codecoachai.ai.agent.weekly.service.WeeklyReportRequestValidator;
import com.codecoachai.ai.agent.weekly.service.WeeklyReportRuleEngine;
import com.codecoachai.ai.agent.weekly.service.WeeklyReportViewAssembler;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentWeeklyReportServiceImpl implements AgentWeeklyReportService {

    private final WeeklyReportFeatureProperties featureProperties;
    private final WeeklyReportRequestValidator requestValidator;
    private final WeeklyReportEvidenceCollector evidenceCollector;
    private final WeeklyReportRuleEngine ruleEngine;
    private final WeeklyReportAiEnhancer aiEnhancer;
    private final AgentWeeklyReportPersistenceService persistenceService;
    private final WeeklyReportViewAssembler viewAssembler;

    @Override
    public AgentWeeklyReportVO current(Long userId, AgentWeeklyReportQueryDTO query) {
        featureProperties.requireWeeklyReportEnabled();
        QueryContext context = requestValidator.query(userId, query, true);
        AgentWeeklyReport report = persistenceService.findIdentity(context);
        StoredView current = report == null ? null : persistenceService.current(report, "CURRENT");
        return current == null
                ? viewAssembler.notGenerated(context, report)
                : viewAssembler.toVO(current);
    }

    @Override
    public AgentWeeklyReportVO generate(Long userId, AgentWeeklyReportGenerateDTO dto) {
        featureProperties.requireWeeklyReportEnabled();
        RequestContext context = requestValidator.generate(userId, dto);
        return generateInternal(context);
    }

    @Override
    public AgentWeeklyReportVO detail(Long userId, Long reportId) {
        featureProperties.requireWeeklyReportEnabled();
        AgentWeeklyReport report = persistenceService.requireOwnedReport(userId, reportId);
        StoredView current = persistenceService.current(report, "DETAIL");
        return current == null
                ? viewAssembler.notGenerated(report)
                : viewAssembler.toVO(current);
    }

    @Override
    public List<AgentWeeklyReportVO> list(Long userId, AgentWeeklyReportQueryDTO query) {
        featureProperties.requireWeeklyReportEnabled();
        QueryContext context = requestValidator.query(userId, query, false);
        List<AgentWeeklyReportVO> result = new ArrayList<>();
        for (AgentWeeklyReport report : persistenceService.listIdentities(context)) {
            StoredView current = persistenceService.current(report, "HISTORY");
            if (current != null) {
                result.add(viewAssembler.toVO(current));
            }
        }
        return result;
    }

    @Override
    public AgentWeeklyReportVO refresh(
            Long userId,
            Long reportId,
            AgentWeeklyReportRefreshDTO dto) {
        featureProperties.requireWeeklyReportEnabled();
        AgentWeeklyReport report = persistenceService.requireOwnedReport(userId, reportId);
        RequestContext context = requestValidator.refresh(userId, report, dto);
        return generateInternal(context);
    }

    private AgentWeeklyReportVO generateInternal(RequestContext context) {
        StoredView idempotent = persistenceService.findIdempotentReplay(context);
        if (idempotent != null) {
            return viewAssembler.toVO(idempotent);
        }

        EvidenceBundle evidence = evidenceCollector.collect(context);
        AggregationResult aggregation = ruleEngine.aggregate(context, evidence);
        if (aggregation == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "周报规则聚合结果不能为空");
        }

        String unchangedResult = "REFRESH".equals(context.getOperation())
                || Boolean.TRUE.equals(context.getForceRefresh())
                ? "NO_CHANGE" : "REUSED";
        StoredView sameInput = persistenceService.findGenerationReplay(
                context, aggregation.getGenerationFingerprint(), unchangedResult);
        if (sameInput != null) {
            return viewAssembler.toVO(sameInput);
        }

        GenerationClaim claim = persistenceService.claimGeneration(
                context, aggregation.getGenerationFingerprint());
        if (claim == null) {
            throw new BusinessException(
                    ErrorCode.STALE_SOURCE_VERSION,
                    "周报生成占位失败，请稍后重试");
        }
        if (claim.getReplay() != null) {
            return viewAssembler.toVO(claim.getReplay());
        }
        if (!claim.isOwner()) {
            StoredView concurrent = persistenceService.findGenerationReplay(
                    context, aggregation.getGenerationFingerprint(), "REPLAY");
            if (concurrent != null) {
                return viewAssembler.toVO(concurrent);
            }
            throw new BusinessException(
                    ErrorCode.STALE_SOURCE_VERSION,
                    "周报正在生成，请稍后重试");
        }

        boolean saved = false;
        try {
            NarrativeResult narrative;
            try {
                narrative = aiEnhancer.enhance(context, aggregation);
            } catch (RuntimeException ex) {
                log.warn("周报 AI 增强失败，使用规则降级 userId={} scope={}",
                        context.getUserId(), context.getTargetScopeKey(), ex);
                narrative = fallbackNarrative(aggregation, "AI 增强暂不可用，已使用规则结果");
            }
            if (narrative == null) {
                narrative = fallbackNarrative(aggregation, "AI 增强结果为空，已使用规则结果");
            }
            SaveCommand command = new SaveCommand();
            command.setContext(context);
            command.setAggregation(aggregation);
            command.setNarrative(narrative);
            StoredView stored = persistenceService.saveClaimed(command, claim.getClaimToken());
            saved = true;
            return viewAssembler.toVO(stored);
        } finally {
            if (!saved) {
                persistenceService.releaseGenerationClaim(context, claim.getClaimToken());
            }
        }
    }

    private NarrativeResult fallbackNarrative(
            AggregationResult aggregation,
            String reason) {
        NarrativeResult narrative = new NarrativeResult();
        narrative.setSummary(aggregation.getRuleSummary());
        narrative.setHypotheses(aggregation.getHypotheses());
        narrative.setResultSource("FALLBACK");
        narrative.setFallback(true);
        narrative.setFallbackReason(reason);
        narrative.setPromptSchemaVersion(null);
        return narrative;
    }
}
