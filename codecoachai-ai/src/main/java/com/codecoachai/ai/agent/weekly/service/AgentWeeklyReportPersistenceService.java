package com.codecoachai.ai.agent.weekly.service;

import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReport;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSnapshot;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSource;
import com.codecoachai.ai.agent.mapper.weekly.AgentWeeklyReportMapper;
import com.codecoachai.ai.agent.mapper.weekly.AgentWeeklyReportSnapshotMapper;
import com.codecoachai.ai.agent.mapper.weekly.AgentWeeklyReportSourceMapper;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.AggregationResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.GenerationClaim;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.NarrativeResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.QueryContext;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.RequestContext;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.SaveCommand;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.StoredView;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportJsonCodec;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportTimeProvider;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportVersions;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentWeeklyReportPersistenceService {

    private static final String IDEMPOTENCY_REUSED = "WEEKLY_REPORT_IDEMPOTENCY_KEY_REUSED";
    private static final Duration GENERATION_CLAIM_TTL = Duration.ofMinutes(10);

    private final AgentWeeklyReportMapper reportMapper;
    private final AgentWeeklyReportSnapshotMapper snapshotMapper;
    private final AgentWeeklyReportSourceMapper sourceMapper;
    private final WeeklyReportJsonCodec jsonCodec;
    private final WeeklyReportTimeProvider timeProvider;

    public AgentWeeklyReport requireOwnedReport(Long userId, Long reportId) {
        AgentWeeklyReport report = reportMapper.selectOwned(userId, reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "周报不存在或无权访问");
        }
        return report;
    }

    public AgentWeeklyReport findIdentity(RequestContext context) {
        return reportMapper.selectIdentity(
                context.getUserId(),
                context.getWeekStartDate(),
                context.getTargetScopeKey(),
                context.getTimezone());
    }

    public AgentWeeklyReport findIdentity(QueryContext context) {
        return reportMapper.selectIdentity(
                context.getUserId(),
                context.getWeekStartDate(),
                context.getTargetScopeKey(),
                context.getTimezone());
    }

    public List<AgentWeeklyReport> listIdentities(QueryContext context) {
        return reportMapper.selectHistoryIdentities(
                context.getUserId(),
                context.getTargetScopeKey(),
                context.getTimezone(),
                context.getWeekStartDate(),
                context.getFromWeekStart(),
                context.getToWeekStart(),
                context.getLimit());
    }

    public StoredView findIdempotentReplay(RequestContext context) {
        if (!StringUtils.hasText(context.getIdempotencyKeyHash())) {
            return null;
        }
        AgentWeeklyReportSnapshot snapshot = snapshotMapper.selectByIdempotencyKey(
                context.getUserId(), context.getIdempotencyKeyHash());
        if (snapshot == null) {
            return null;
        }
        validateIdempotentSnapshot(context, snapshot);
        AgentWeeklyReport report = requireOwnedReport(context.getUserId(), snapshot.getWeeklyReportId());
        validateIdentity(context, report);
        return stored(report, snapshot, "REPLAY");
    }

    public StoredView findGenerationReplay(
            RequestContext context,
            String generationFingerprint,
            String operationResult) {
        if (!StringUtils.hasText(generationFingerprint)) {
            return null;
        }
        AgentWeeklyReport report = findIdentity(context);
        if (report == null) {
            return null;
        }
        AgentWeeklyReportSnapshot snapshot = snapshotMapper.selectByGenerationFingerprint(
                context.getUserId(), report.getId(), generationFingerprint);
        return snapshot == null ? null : stored(report, snapshot, operationResult);
    }

    public StoredView current(AgentWeeklyReport report, String operationResult) {
        if (report == null || report.getCurrentSnapshotId() == null) {
            return null;
        }
        AgentWeeklyReportSnapshot snapshot = snapshotMapper.selectOwnedSnapshot(
                report.getUserId(), report.getId(), report.getCurrentSnapshotId());
        return snapshot == null ? null : stored(report, snapshot, operationResult);
    }

    @Transactional(rollbackFor = Exception.class)
    public GenerationClaim claimGeneration(
            RequestContext context,
            String generationFingerprint) {
        reportMapper.ensureIdentity(
                context.getUserId(),
                context.getTargetJobId(),
                context.getTargetScopeKey(),
                context.getWeekStartDate(),
                context.getWeekEndDate(),
                context.getTimezone());
        AgentWeeklyReport report = reportMapper.selectIdentityForUpdate(
                context.getUserId(),
                context.getWeekStartDate(),
                context.getTargetScopeKey(),
                context.getTimezone());
        if (report == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "周报身份创建失败，请稍后重试");
        }

        StoredView replay = replayUnderLock(context, report, generationFingerprint);
        if (replay != null) {
            GenerationClaim claim = new GenerationClaim();
            claim.setReplay(replay);
            return claim;
        }

        LocalDateTime claimedAt = LocalDateTime.ofInstant(
                timeProvider.now(), java.time.ZoneOffset.UTC);
        if (StringUtils.hasText(report.getGenerationClaimToken())
                && !claimExpired(report, claimedAt)) {
            validateActiveIdempotencyClaim(context, report);
            return new GenerationClaim();
        }

        String claimToken = UUID.randomUUID().toString();
        try {
            int updated = reportMapper.claimGeneration(
                    context.getUserId(),
                    report.getId(),
                    generationFingerprint,
                    claimToken,
                    context.getIdempotencyKeyHash(),
                    context.getIdempotencyPayloadHash(),
                    claimedAt);
            if (updated != 1) {
                throw new BusinessException(
                        ErrorCode.STALE_SOURCE_VERSION,
                        "周报生成占位失败，请稍后重试");
            }
        } catch (DuplicateKeyException ex) {
            throw idempotencyConflict();
        }

        GenerationClaim claim = new GenerationClaim();
        claim.setOwner(true);
        claim.setClaimToken(claimToken);
        return claim;
    }

    @Transactional(rollbackFor = Exception.class)
    public StoredView save(SaveCommand command) {
        return saveClaimed(command, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public StoredView saveClaimed(SaveCommand command, String claimToken) {
        RequestContext context = command.getContext();
        AggregationResult aggregation = command.getAggregation();
        NarrativeResult narrative = command.getNarrative();

        reportMapper.ensureIdentity(
                context.getUserId(),
                context.getTargetJobId(),
                context.getTargetScopeKey(),
                context.getWeekStartDate(),
                context.getWeekEndDate(),
                context.getTimezone());
        AgentWeeklyReport report = reportMapper.selectIdentityForUpdate(
                context.getUserId(),
                context.getWeekStartDate(),
                context.getTargetScopeKey(),
                context.getTimezone());
        if (report == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "周报身份创建失败，请稍后重试");
        }

        StoredView replay = replayUnderLock(
                context, report, aggregation.getGenerationFingerprint());
        if (replay != null) {
            clearClaim(context, report, claimToken);
            return replay;
        }
        if (StringUtils.hasText(claimToken)
                && !Objects.equals(claimToken, report.getGenerationClaimToken())) {
            throw new BusinessException(
                    ErrorCode.STALE_SOURCE_VERSION,
                    "周报生成占位已失效，请重试");
        }

        int nextVersion = Math.max(
                report.getSnapshotVersion() == null ? 0 : report.getSnapshotVersion(),
                value(snapshotMapper.selectMaxVersion(context.getUserId(), report.getId()))) + 1;
        AgentWeeklyReportSnapshot snapshot = snapshot(command, report.getId(), nextVersion);
        try {
            snapshotMapper.insertSnapshot(snapshot);
        } catch (DuplicateKeyException ex) {
            StoredView duplicateReplay = resolveDuplicate(context, report, aggregation);
            if (duplicateReplay != null) {
                clearClaim(context, report, claimToken);
                return duplicateReplay;
            }
            throw ex;
        }

        List<AgentWeeklyReportSource> sources = new ArrayList<>();
        for (AgentWeeklyReportSource source : safeList(aggregation.getSources())) {
            source.setUserId(context.getUserId());
            source.setSnapshotId(snapshot.getId());
            sources.add(source);
        }
        if (!sources.isEmpty()) {
            sourceMapper.insertBatch(sources);
        }

        int updated = reportMapper.updateCurrentSnapshot(
                context.getUserId(),
                report.getId(),
                snapshot.getId(),
                snapshot.getReportStatus(),
                nextVersion,
                snapshot.getSummary(),
                snapshot.getConfidenceLevel(),
                snapshot.getFallback(),
                snapshot.getFallbackReason());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION,
                    "周报当前快照已变化，请刷新后重试");
        }
        clearClaim(context, report, claimToken);
        report.setCurrentSnapshotId(snapshot.getId());
        report.setReportStatus(snapshot.getReportStatus());
        report.setSnapshotVersion(nextVersion);
        report.setSummary(snapshot.getSummary());
        report.setConfidenceLevel(snapshot.getConfidenceLevel());
        report.setFallback(snapshot.getFallback());
        report.setFallbackReason(snapshot.getFallbackReason());
        String operationResult = "REFRESH".equals(context.getOperation()) ? "REFRESHED" : "CREATED";
        return stored(report, snapshot, operationResult);
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseGenerationClaim(RequestContext context, String claimToken) {
        if (!StringUtils.hasText(claimToken)) {
            return;
        }
        AgentWeeklyReport report = findIdentity(context);
        if (report != null) {
            reportMapper.clearGenerationClaim(
                    context.getUserId(), report.getId(), claimToken);
        }
    }

    private StoredView resolveDuplicate(
            RequestContext context,
            AgentWeeklyReport report,
            AggregationResult aggregation) {
        if (StringUtils.hasText(context.getIdempotencyKeyHash())) {
            AgentWeeklyReportSnapshot byKey = snapshotMapper.selectByIdempotencyKey(
                    context.getUserId(), context.getIdempotencyKeyHash());
            if (byKey != null) {
                validateIdempotentSnapshot(context, byKey);
                if (!Objects.equals(report.getId(), byKey.getWeeklyReportId())) {
                    throw idempotencyConflict();
                }
                return stored(report, byKey, "REPLAY");
            }
        }
        AgentWeeklyReportSnapshot byGeneration = snapshotMapper.selectByGenerationFingerprint(
                context.getUserId(), report.getId(), aggregation.getGenerationFingerprint());
        return byGeneration == null ? null : stored(report, byGeneration, "NO_CHANGE");
    }

    private StoredView replayUnderLock(
            RequestContext context,
            AgentWeeklyReport report,
            String generationFingerprint) {
        if (StringUtils.hasText(context.getIdempotencyKeyHash())) {
            AgentWeeklyReportSnapshot byKey = snapshotMapper.selectByIdempotencyKey(
                    context.getUserId(), context.getIdempotencyKeyHash());
            if (byKey != null) {
                validateIdempotentSnapshot(context, byKey);
                if (!Objects.equals(report.getId(), byKey.getWeeklyReportId())) {
                    throw idempotencyConflict();
                }
                return stored(report, byKey, "REPLAY");
            }
        }
        AgentWeeklyReportSnapshot byGeneration = snapshotMapper.selectByGenerationFingerprint(
                context.getUserId(), report.getId(), generationFingerprint);
        return byGeneration == null ? null : stored(report, byGeneration, "NO_CHANGE");
    }

    private void validateActiveIdempotencyClaim(
            RequestContext context,
            AgentWeeklyReport report) {
        if (StringUtils.hasText(context.getIdempotencyKeyHash())
                && Objects.equals(
                        context.getIdempotencyKeyHash(),
                        report.getGenerationClaimIdempotencyKeyHash())
                && !Objects.equals(
                        context.getIdempotencyPayloadHash(),
                        report.getGenerationClaimPayloadHash())) {
            throw idempotencyConflict();
        }
    }

    private boolean claimExpired(AgentWeeklyReport report, LocalDateTime nowUtc) {
        LocalDateTime claimedAt = report.getGenerationClaimedAt();
        return claimedAt == null
                || !claimedAt.plus(GENERATION_CLAIM_TTL).isAfter(nowUtc);
    }

    private void clearClaim(
            RequestContext context,
            AgentWeeklyReport report,
            String claimToken) {
        if (StringUtils.hasText(claimToken)) {
            int cleared = reportMapper.clearGenerationClaim(
                    context.getUserId(), report.getId(), claimToken);
            if (cleared != 1) {
                throw new BusinessException(
                        ErrorCode.STALE_SOURCE_VERSION,
                        "周报生成占位已变化，请重试");
            }
        }
    }

    private AgentWeeklyReportSnapshot snapshot(
            SaveCommand command,
            Long reportId,
            int version) {
        RequestContext context = command.getContext();
        AggregationResult aggregation = command.getAggregation();
        NarrativeResult narrative = command.getNarrative();
        AgentWeeklyReportSnapshot snapshot = new AgentWeeklyReportSnapshot();
        snapshot.setUserId(context.getUserId());
        snapshot.setWeeklyReportId(reportId);
        snapshot.setSnapshotVersion(version);
        snapshot.setWeekStartDate(context.getWeekStartDate());
        snapshot.setWeekEndDate(context.getWeekEndDate());
        snapshot.setTargetScopeKey(context.getTargetScopeKey());
        snapshot.setRangeStartUtc(context.getRangeStartUtc());
        snapshot.setRangeEndUtc(context.getRangeEndUtc());
        snapshot.setSourceCutoffAt(context.getSourceCutoffAt());
        snapshot.setInputHash(aggregation.getInputHash());
        snapshot.setGenerationFingerprint(aggregation.getGenerationFingerprint());
        snapshot.setIdempotencyKeyHash(context.getIdempotencyKeyHash());
        snapshot.setIdempotencyPayloadHash(context.getIdempotencyPayloadHash());
        snapshot.setRequestId(context.getRequestId());
        snapshot.setCalculationVersion(WeeklyReportVersions.CALCULATION_VERSION);
        snapshot.setPromptSchemaVersion(firstText(
                narrative.getPromptSchemaVersion(), WeeklyReportVersions.AI_PROMPT_SCHEMA_VERSION));
        snapshot.setOutputSchemaVersion(WeeklyReportVersions.OUTPUT_SCHEMA_VERSION);
        snapshot.setReportStatus(context.getReportStatus());
        snapshot.setSummary(firstText(narrative.getSummary(), aggregation.getRuleSummary()));
        snapshot.setConfidenceLevel(aggregation.getConfidenceLevel());
        snapshot.setFactsJson(jsonCodec.toJson(aggregation.getFacts()));
        snapshot.setSignalsJson(jsonCodec.toJson(aggregation.getSignals()));
        snapshot.setHypothesesJson(jsonCodec.toJson(narrative.getHypotheses()));
        snapshot.setExperimentSuggestionsJson(jsonCodec.toJson(aggregation.getExperimentSuggestions()));
        snapshot.setPlanDraftJson(jsonCodec.toJson(aggregation.getPlanDraft()));
        snapshot.setCoverageJson(jsonCodec.toJson(aggregation.getCoverage()));
        snapshot.setResultSource(firstText(narrative.getResultSource(), "RULE"));
        snapshot.setFallback(Boolean.TRUE.equals(narrative.getFallback()) ? 1 : 0);
        snapshot.setFallbackReason(narrative.getFallbackReason());
        snapshot.setTraceId(context.getTraceId());
        snapshot.setAiCallLogId(narrative.getAiCallLogId());
        snapshot.setGeneratedAt(context.getGeneratedAt());
        return snapshot;
    }

    private StoredView stored(
            AgentWeeklyReport report,
            AgentWeeklyReportSnapshot snapshot,
            String operationResult) {
        StoredView stored = new StoredView();
        stored.setReport(report);
        stored.setSnapshot(snapshot);
        stored.setSources(sourceMapper.selectBySnapshot(report.getUserId(), snapshot.getId()));
        stored.setHistory(snapshotMapper.selectHistory(report.getUserId(), report.getId()));
        stored.setOperationResult(operationResult);
        return stored;
    }

    private void validateIdempotentSnapshot(
            RequestContext context,
            AgentWeeklyReportSnapshot snapshot) {
        if (!Objects.equals(context.getIdempotencyPayloadHash(), snapshot.getIdempotencyPayloadHash())) {
            throw idempotencyConflict();
        }
    }

    private void validateIdentity(RequestContext context, AgentWeeklyReport report) {
        if (!Objects.equals(context.getWeekStartDate(), report.getWeekStartDate())
                || !Objects.equals(context.getTargetScopeKey(), report.getTargetScopeKey())
                || !Objects.equals(context.getTimezone(), report.getTimezone())) {
            throw idempotencyConflict();
        }
    }

    private BusinessException idempotencyConflict() {
        return new BusinessException(
                ErrorCode.STALE_SOURCE_VERSION,
                IDEMPOTENCY_REUSED + "：同一幂等键不能用于不同的周报请求");
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first.trim() : fallback;
    }
}
