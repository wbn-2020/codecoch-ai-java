package com.codecoachai.ai.agent.weekly.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReport;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSnapshot;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReportSource;
import com.codecoachai.ai.agent.domain.vo.weekly.AgentWeeklyReportVO;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.StoredView;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportJsonCodec;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportTimeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyReportViewAssemblerTest {

    @Mock
    private WeeklyReportTimeProvider timeProvider;

    @Test
    void malformedHistoricalJsonFallsBackWithoutMakingPlanDraftAvailable() {
        when(timeProvider.now()).thenReturn(Instant.parse("2026-07-20T00:00:00Z"));
        AgentWeeklyReport report = new AgentWeeklyReport();
        report.setId(1L);
        report.setUserId(10L);
        report.setTargetScopeKey("ALL");
        report.setWeekStartDate(LocalDate.of(2026, 7, 13));
        report.setWeekEndDate(LocalDate.of(2026, 7, 19));
        report.setTimezone("Asia/Shanghai");
        report.setCurrentSnapshotId(101L);

        AgentWeeklyReportSnapshot snapshot = new AgentWeeklyReportSnapshot();
        snapshot.setId(101L);
        snapshot.setWeeklyReportId(1L);
        snapshot.setUserId(10L);
        snapshot.setSnapshotVersion(2);
        snapshot.setWeekStartDate(report.getWeekStartDate());
        snapshot.setWeekEndDate(report.getWeekEndDate());
        snapshot.setReportStatus("IN_PROGRESS");
        snapshot.setConfidenceLevel("FACT_ONLY");
        snapshot.setFallback(1);
        snapshot.setFactsJson("{broken");
        snapshot.setSignalsJson("[]");
        snapshot.setHypothesesJson("[]");
        snapshot.setExperimentSuggestionsJson("[]");
        snapshot.setPlanDraftJson("{broken");
        snapshot.setCoverageJson("{broken");
        snapshot.setSourceCutoffAt(LocalDateTime.of(2026, 7, 18, 12, 0));

        AgentWeeklyReportSource source = new AgentWeeklyReportSource();
        source.setSourceType("JOB_APPLICATION");
        source.setSourceId(88L);
        source.setInclusionStatus("INCLUDED");
        source.setMetadataJson("{\"matured\":true}");

        StoredView stored = new StoredView();
        stored.setReport(report);
        stored.setSnapshot(snapshot);
        stored.setSources(List.of(source));
        stored.setHistory(List.of(snapshot));
        stored.setOperationResult("CURRENT");

        AgentWeeklyReportVO vo = assembler().toVO(stored);

        assertTrue(vo.getFacts().isEmpty());
        assertTrue(vo.getSignals().isEmpty());
        assertFalse(Boolean.TRUE.equals(vo.getPlanDraft().getAvailable()));
        assertTrue(vo.getPlanDraft().getUnavailableReason().contains("暂不可用"));
        assertEquals(1, vo.getCoverage().getSources().size());
        assertEquals(Boolean.TRUE, vo.getCoverage().getSources().get(0).getMetadata().get("matured"));
        assertEquals(1, vo.getSnapshotHistory().size());
        assertTrue(Boolean.TRUE.equals(vo.getSnapshotHistory().get(0).getCurrent()));
        assertEquals("COMPLETED", vo.getReportStatus());
        assertEquals("COMPLETED", vo.getRange().getWindowStatus());
        assertEquals("IN_PROGRESS", vo.getSnapshotHistory().get(0).getReportStatus());
    }

    @Test
    void notGeneratedUsesRequestedWeekWithoutCreatingFakeCutoff() {
        when(timeProvider.now()).thenReturn(Instant.parse("2026-07-18T12:00:00Z"));
        var context = new com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.QueryContext();
        context.setTargetScopeKey("ALL");
        context.setWeekStartDate(LocalDate.of(2026, 7, 13));
        context.setTimezone("Asia/Shanghai");
        context.setZoneId(java.time.ZoneId.of("Asia/Shanghai"));

        AgentWeeklyReportVO vo = assembler().notGenerated(context, null);

        assertEquals("NOT_GENERATED", vo.getReportStatus());
        assertEquals("IN_PROGRESS", vo.getRange().getWindowStatus());
        assertEquals(null, vo.getRange().getSourceCutoffAt());
        assertFalse(Boolean.TRUE.equals(vo.getPlanDraft().getAvailable()));
    }

    private WeeklyReportViewAssembler assembler() {
        return new WeeklyReportViewAssembler(
                new WeeklyReportJsonCodec(new ObjectMapper().findAndRegisterModules()),
                timeProvider);
    }
}
