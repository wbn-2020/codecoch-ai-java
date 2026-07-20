package com.codecoachai.ai.agent.weekly.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReport;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSnapshot;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSource;
import com.codecoachai.ai.agent.mapper.weekly.AgentWeeklyReportMapper;
import com.codecoachai.ai.agent.mapper.weekly.AgentWeeklyReportSnapshotMapper;
import com.codecoachai.ai.agent.mapper.weekly.AgentWeeklyReportSourceMapper;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.AggregationResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.GenerationClaim;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.NarrativeResult;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.RequestContext;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.SaveCommand;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.StoredView;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportJsonCodec;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportTimeProvider;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentWeeklyReportPersistenceServiceTest {

    @Mock
    private AgentWeeklyReportMapper reportMapper;
    @Mock
    private AgentWeeklyReportSnapshotMapper snapshotMapper;
    @Mock
    private AgentWeeklyReportSourceMapper sourceMapper;
    @Mock
    private WeeklyReportJsonCodec jsonCodec;
    @Mock
    private WeeklyReportTimeProvider timeProvider;

    @Test
    void sameIdempotencyKeyWithDifferentPayloadReturnsConflict() {
        RequestContext context = context();
        context.setIdempotencyKeyHash("key-hash");
        context.setIdempotencyPayloadHash("payload-new");
        AgentWeeklyReportSnapshot snapshot = snapshot(101L, 1L);
        snapshot.setIdempotencyPayloadHash("payload-old");
        when(snapshotMapper.selectByIdempotencyKey(10L, "key-hash")).thenReturn(snapshot);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().findIdempotentReplay(context));

        assertTrue(exception.getMessage().contains("WEEKLY_REPORT_IDEMPOTENCY_KEY_REUSED"));
        verify(reportMapper, never()).selectOwned(any(), any());
    }

    @Test
    void sameGenerationReturnsNoChangeWithoutWritingSnapshot() {
        RequestContext context = context();
        AggregationResult aggregation = aggregation();
        AgentWeeklyReport report = report();
        AgentWeeklyReportSnapshot existing = snapshot(101L, report.getId());
        when(reportMapper.selectIdentityForUpdate(
                10L, context.getWeekStartDate(), "ALL", "Asia/Shanghai")).thenReturn(report);
        when(snapshotMapper.selectByGenerationFingerprint(
                10L, report.getId(), aggregation.getGenerationFingerprint())).thenReturn(existing);
        when(sourceMapper.selectBySnapshot(10L, existing.getId())).thenReturn(List.of());
        when(snapshotMapper.selectHistory(10L, report.getId())).thenReturn(List.of(existing));

        StoredView result = service().save(command(context, aggregation));

        assertEquals("NO_CHANGE", result.getOperationResult());
        assertEquals(existing.getId(), result.getSnapshot().getId());
        verify(snapshotMapper, never()).insertSnapshot(any());
        verify(reportMapper, never()).updateCurrentSnapshot(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void newInputWritesImmutableSnapshotSourcesAndCurrentPointer() {
        RequestContext context = context();
        AggregationResult aggregation = aggregation();
        AgentWeeklyReportSource source = new AgentWeeklyReportSource();
        source.setSourceType("JOB_APPLICATION");
        source.setSourceId(88L);
        source.setInclusionStatus("INCLUDED");
        aggregation.setSources(List.of(source));
        AgentWeeklyReport report = report();
        when(reportMapper.selectIdentityForUpdate(
                10L, context.getWeekStartDate(), "ALL", "Asia/Shanghai")).thenReturn(report);
        when(snapshotMapper.selectMaxVersion(10L, report.getId())).thenReturn(0);
        when(jsonCodec.toJson(any())).thenReturn("[]");
        when(snapshotMapper.insertSnapshot(any())).thenAnswer(invocation -> {
            AgentWeeklyReportSnapshot inserted = invocation.getArgument(0);
            inserted.setId(501L);
            return 1;
        });
        when(reportMapper.updateCurrentSnapshot(
                10L, report.getId(), 501L, "IN_PROGRESS", 1,
                "规则摘要", "FACT_ONLY", 0, null)).thenReturn(1);
        when(sourceMapper.selectBySnapshot(10L, 501L)).thenReturn(List.of(source));
        when(snapshotMapper.selectHistory(10L, report.getId())).thenReturn(List.of());

        StoredView result = service().save(command(context, aggregation));

        assertEquals("CREATED", result.getOperationResult());
        assertEquals(501L, result.getSnapshot().getId());
        assertEquals(1, result.getSnapshot().getSnapshotVersion());
        assertEquals(501L, source.getSnapshotId());
        assertEquals(10L, source.getUserId());
        verify(sourceMapper).insertBatch(List.of(source));
        verify(reportMapper).updateCurrentSnapshot(
                10L, report.getId(), 501L, "IN_PROGRESS", 1,
                "规则摘要", "FACT_ONLY", 0, null);
    }

    @Test
    void ownerCheckRejectsMissingReport() {
        when(reportMapper.selectOwned(10L, 99L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().requireOwnedReport(10L, 99L));

        assertTrue(exception.getMessage().contains("无权访问"));
    }

    @Test
    void activeGenerationClaimMakesConcurrentRequestAReplayWaiter() {
        RequestContext context = context();
        context.setIdempotencyKeyHash("key-hash");
        context.setIdempotencyPayloadHash("payload-hash");
        AgentWeeklyReport report = report();
        report.setGenerationClaimFingerprint("generation-001");
        report.setGenerationClaimToken("claim-owner");
        report.setGenerationClaimIdempotencyKeyHash("key-hash");
        report.setGenerationClaimPayloadHash("payload-hash");
        report.setGenerationClaimedAt(LocalDateTime.of(2026, 7, 20, 0, 0));
        when(timeProvider.now()).thenReturn(Instant.parse("2026-07-20T00:01:00Z"));
        when(reportMapper.selectIdentityForUpdate(
                10L, context.getWeekStartDate(), "ALL", "Asia/Shanghai")).thenReturn(report);

        GenerationClaim claim = service().claimGeneration(context, "generation-001");

        assertFalse(claim.isOwner());
        assertNull(claim.getReplay());
        verify(reportMapper, never()).claimGeneration(
                anyLong(), anyLong(), anyString(), anyString(),
                any(), any(), any(LocalDateTime.class));
    }

    private AgentWeeklyReportPersistenceService service() {
        return new AgentWeeklyReportPersistenceService(
                reportMapper, snapshotMapper, sourceMapper, jsonCodec, timeProvider);
    }

    private SaveCommand command(RequestContext context, AggregationResult aggregation) {
        NarrativeResult narrative = new NarrativeResult();
        narrative.setSummary("规则摘要");
        narrative.setHypotheses(List.of());
        narrative.setResultSource("RULE");
        narrative.setFallback(false);
        SaveCommand command = new SaveCommand();
        command.setContext(context);
        command.setAggregation(aggregation);
        command.setNarrative(narrative);
        return command;
    }

    private RequestContext context() {
        RequestContext context = new RequestContext();
        context.setUserId(10L);
        context.setTargetScopeKey("ALL");
        context.setWeekStartDate(LocalDate.of(2026, 7, 13));
        context.setWeekEndDate(LocalDate.of(2026, 7, 19));
        context.setTimezone("Asia/Shanghai");
        context.setRangeStartUtc(LocalDateTime.of(2026, 7, 12, 16, 0));
        context.setRangeEndUtc(LocalDateTime.of(2026, 7, 19, 16, 0));
        context.setSourceCutoffAt(LocalDateTime.of(2026, 7, 18, 12, 0));
        context.setGeneratedAt(context.getSourceCutoffAt());
        context.setReportStatus("IN_PROGRESS");
        context.setOperation("GENERATE");
        context.setTraceId("trace-001");
        return context;
    }

    private AggregationResult aggregation() {
        AggregationResult aggregation = new AggregationResult();
        aggregation.setInputHash("input-001");
        aggregation.setGenerationFingerprint("generation-001");
        aggregation.setConfidenceLevel("FACT_ONLY");
        aggregation.setRuleSummary("规则摘要");
        aggregation.setFacts(List.of());
        aggregation.setSignals(List.of());
        aggregation.setHypotheses(List.of());
        aggregation.setExperimentSuggestions(List.of());
        aggregation.setSources(List.of());
        return aggregation;
    }

    private AgentWeeklyReport report() {
        AgentWeeklyReport report = new AgentWeeklyReport();
        report.setId(1L);
        report.setUserId(10L);
        report.setTargetScopeKey("ALL");
        report.setWeekStartDate(LocalDate.of(2026, 7, 13));
        report.setWeekEndDate(LocalDate.of(2026, 7, 19));
        report.setTimezone("Asia/Shanghai");
        report.setSnapshotVersion(0);
        return report;
    }

    private AgentWeeklyReportSnapshot snapshot(Long id, Long reportId) {
        AgentWeeklyReportSnapshot snapshot = new AgentWeeklyReportSnapshot();
        snapshot.setId(id);
        snapshot.setUserId(10L);
        snapshot.setWeeklyReportId(reportId);
        snapshot.setSnapshotVersion(1);
        snapshot.setWeekStartDate(LocalDate.of(2026, 7, 13));
        snapshot.setWeekEndDate(LocalDate.of(2026, 7, 19));
        snapshot.setReportStatus("IN_PROGRESS");
        snapshot.setConfidenceLevel("FACT_ONLY");
        snapshot.setFallback(0);
        return snapshot;
    }
}
