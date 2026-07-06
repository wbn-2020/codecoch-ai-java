package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.agent.domain.dto.AgentMemoryCreateDTO;
import com.codecoachai.ai.agent.domain.dto.AgentMemoryQueryDTO;
import com.codecoachai.ai.agent.domain.entity.AgentMemory;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.ReadinessScoreRecord;
import com.codecoachai.ai.agent.domain.entity.SkillGrowthSnapshot;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.growth.GrowthOverviewVO;
import com.codecoachai.ai.agent.domain.vo.growth.ReadinessScoreRecordVO;
import com.codecoachai.ai.agent.domain.vo.growth.SkillGrowthSnapshotVO;
import com.codecoachai.ai.agent.domain.vo.memory.AgentMemoryVO;
import com.codecoachai.ai.agent.mapper.AgentMemoryMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.ReadinessScoreRecordMapper;
import com.codecoachai.ai.agent.mapper.SkillGrowthSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentGrowthServiceImplTest {

    @Mock
    private AgentTaskMapper agentTaskMapper;
    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private AgentReviewMapper agentReviewMapper;
    @Mock
    private SkillGrowthSnapshotMapper skillGrowthSnapshotMapper;
    @Mock
    private ReadinessScoreRecordMapper readinessScoreRecordMapper;
    @Mock
    private AgentMemoryMapper agentMemoryMapper;

    @Test
    void growthOverviewHidesStrongScoresAndSuggestsEvidenceActionsWhenDataIsInsufficient() {
        AgentGrowthServiceImpl service = service();
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        when(agentRunMapper.selectList(any())).thenReturn(List.of());
        when(agentReviewMapper.selectCount(any())).thenReturn(0L);
        when(agentMemoryMapper.selectCount(any())).thenReturn(0L);

        GrowthOverviewVO vo = service.growthOverview(10L);

        assertNull(vo.getReadinessScore());
        assertNull(vo.getTaskCompletionRate());
        assertNull(vo.getAgentSuccessRate());
        assertEquals("LOW", vo.getConfidenceLevel());
        assertEquals(0, vo.getEvidenceCount());
        assertFalse(vo.getDisplayPolicy().getShowStrongScore());
        assertFalse(vo.getDisplayPolicy().getShowPercentileComparison());
        assertFalse(vo.getDisplayPolicy().getShowGapPercentage());
        assertNotNull(vo.getColdStartReason());
        assertFalse(vo.getColdStartReason().isBlank());
        assertFalse(vo.getNextEvidenceActions().isEmpty());
        assertEquals(2, vo.getDataSourceLabels().size());
    }

    @Test
    void growthOverviewShowsTrustedScoresWhenEvidenceGateIsMet() {
        AgentGrowthServiceImpl service = service();
        LocalDate today = LocalDate.now();
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                task(1L, today.minusDays(8), AgentTaskStatusEnum.DONE.name(), "JAVA", "Java Basics"),
                task(2L, today.minusDays(4), AgentTaskStatusEnum.DONE.name(), "MYSQL", "MySQL"),
                task(3L, today.minusDays(1), AgentTaskStatusEnum.DONE.name(), "JAVA", "Java Basics")));
        when(agentRunMapper.selectList(any())).thenReturn(List.of(
                run(11L, today.minusDays(8), AgentRunStatusEnum.SUCCESS.name()),
                run(12L, today.minusDays(4), AgentRunStatusEnum.SUCCESS.name())));
        when(agentReviewMapper.selectCount(any())).thenReturn(1L);
        when(agentMemoryMapper.selectCount(any())).thenReturn(1L);

        GrowthOverviewVO vo = service.growthOverview(10L);

        assertEquals(100, vo.getReadinessScore());
        assertEquals(100D, vo.getTaskCompletionRate());
        assertEquals(100D, vo.getAgentSuccessRate());
        assertEquals("HIGH", vo.getConfidenceLevel());
        assertEquals(3, vo.getEvidenceCount());
        assertTrue(vo.getDisplayPolicy().getShowStrongScore());
        assertFalse(vo.getDisplayPolicy().getShowPercentileComparison());
        assertFalse(vo.getDisplayPolicy().getShowGapPercentage());
        assertNull(vo.getColdStartReason());
        assertTrue(vo.getNextEvidenceActions().isEmpty());
        assertEquals("Java Basics", vo.getTopSkills().get(0).getName());
        assertEquals(2, vo.getDataSourceLabels().size());
    }

    @Test
    void growthOverviewKeepsCumulativeReviewAndMemoryCountsOutOfRecentEvidence() {
        AgentGrowthServiceImpl service = service();
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        when(agentRunMapper.selectList(any())).thenReturn(List.of());
        when(agentReviewMapper.selectCount(any())).thenReturn(4L);
        when(agentMemoryMapper.selectCount(any())).thenReturn(3L);

        GrowthOverviewVO vo = service.growthOverview(10L);

        assertEquals(4L, vo.getTotalReviewCount());
        assertEquals(3L, vo.getTotalMemoryCount());
        assertEquals(0, vo.getEvidenceCount());
        assertEquals(2, vo.getDataSourceLabels().size());
        assertEquals("LOW", vo.getConfidenceLevel());
        assertNull(vo.getReadinessScore());
    }

    @Test
    void skillTrendMarksLowEvidenceSnapshotsWithColdStartGuidance() {
        AgentGrowthServiceImpl service = service();
        when(skillGrowthSnapshotMapper.selectList(any())).thenReturn(List.of(
                skillSnapshot(1L, LocalDate.now(), "JAVA", "Java Basics", 70, 1, 0)));

        List<SkillGrowthSnapshotVO> trend = service.skillTrend(10L, 7);

        SkillGrowthSnapshotVO vo = trend.get(0);
        assertEquals(1, vo.getEvidenceCount());
        assertEquals("LOW", vo.getConfidenceLevel());
        assertEquals(2, vo.getDataSourceLabels().size());
        assertNotNull(vo.getColdStartReason());
        assertFalse(vo.getColdStartReason().isBlank());
        assertFalse(vo.getNextEvidenceActions().isEmpty());
    }

    @Test
    void skillTrendMarksTrustedSnapshotsWithoutColdStartGuidance() {
        AgentGrowthServiceImpl service = service();
        when(skillGrowthSnapshotMapper.selectList(any())).thenReturn(List.of(
                skillSnapshot(2L, LocalDate.now(), "MYSQL", "MySQL", 88, 3, 2)));

        List<SkillGrowthSnapshotVO> trend = service.skillTrend(10L, 30);

        SkillGrowthSnapshotVO vo = trend.get(0);
        assertEquals(3, vo.getEvidenceCount());
        assertEquals("HIGH", vo.getConfidenceLevel());
        assertNull(vo.getColdStartReason());
        assertTrue(vo.getNextEvidenceActions().isEmpty());
        assertEquals(2, vo.getDataSourceLabels().size());
    }

    @Test
    void readinessTrendMarksSingleRecordEvidenceAsLowConfidence() {
        AgentGrowthServiceImpl service = service();
        when(readinessScoreRecordMapper.selectList(any())).thenReturn(List.of(
                readinessRecord(3L, LocalDate.now(), 72, "{}")));

        List<ReadinessScoreRecordVO> trend = service.readinessTrend(10L, 14);

        ReadinessScoreRecordVO vo = trend.get(0);
        assertEquals(0, vo.getEvidenceCount());
        assertEquals("LOW", vo.getConfidenceLevel());
        assertEquals(2, vo.getDataSourceLabels().size());
        assertNotNull(vo.getColdStartReason());
        assertFalse(vo.getColdStartReason().isBlank());
        assertFalse(vo.getNextEvidenceActions().isEmpty());
    }

    @Test
    void readinessTrendUsesSameEvidenceGateAsOverviewWhenTaskEvidenceIsPresent() {
        AgentGrowthServiceImpl service = service();
        when(readinessScoreRecordMapper.selectList(any())).thenReturn(List.of(
                readinessRecord(4L, LocalDate.now(), 86, "{\"taskCount\":3,\"doneCount\":2}")));

        List<ReadinessScoreRecordVO> trend = service.readinessTrend(10L, 30);

        ReadinessScoreRecordVO vo = trend.get(0);
        assertEquals(3, vo.getEvidenceCount());
        assertEquals("HIGH", vo.getConfidenceLevel());
        assertNull(vo.getColdStartReason());
        assertTrue(vo.getNextEvidenceActions().isEmpty());
        assertEquals(2, vo.getDataSourceLabels().size());
    }

    @Test
    void createMemoryStoresRequestedOwnerAndDefaults() {
        AgentGrowthServiceImpl service = service();
        AgentMemoryCreateDTO dto = new AgentMemoryCreateDTO();
        dto.setContent("Focus more on MySQL index topics during review.");

        AgentMemoryVO vo = service.createMemory(10L, dto);

        ArgumentCaptor<AgentMemory> memoryCaptor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryMapper).insert(memoryCaptor.capture());
        AgentMemory memory = memoryCaptor.getValue();
        assertEquals(10L, memory.getUserId());
        assertEquals("USER_NOTE", memory.getMemoryType());
        assertEquals("Focus more on MySQL index topics during review.", memory.getContent());
        assertEquals("MANUAL", memory.getSourceType());
        assertEquals(BigDecimal.valueOf(0.9), memory.getConfidence());
        assertEquals(1, memory.getEnabled());
        assertEquals("Focus more on MySQL index topics during review.", vo.getContent());
    }

    @Test
    void createMemoryMarksManualEnabledMemoryAsConfirmedEvidence() {
        AgentGrowthServiceImpl service = service();
        AgentMemoryCreateDTO dto = new AgentMemoryCreateDTO();
        dto.setContent("Review system design tradeoffs before interviews.");
        dto.setConfidence(BigDecimal.valueOf(0.9));

        AgentMemoryVO vo = service.createMemory(10L, dto);

        assertEquals("CONFIRMED", vo.getMemoryStatus());
        assertEquals("VERIFIED", vo.getEvidenceTrustStatus());
        assertTrue(vo.getCanBeEvidence());
        assertFalse(vo.getLowConfidence());
        assertNotNull(vo.getConfirmedAt());
        assertTrue(vo.getImpactPreview().contains("AGENT_TASK"));
    }

    @Test
    void pageMemoriesMarksAgentDisabledMemoryAsCandidateUntilConfirmed() {
        AgentGrowthServiceImpl service = service();
        Page<AgentMemory> page = Page.of(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(memory(88L, 10L, 0, "AGENT_REVIEW", BigDecimal.valueOf(0.75))));
        when(agentMemoryMapper.selectPage(any(), any())).thenReturn(page);

        AgentMemoryVO vo = service.pageMemories(10L, new AgentMemoryQueryDTO()).getRecords().get(0);

        assertEquals("CANDIDATE", vo.getMemoryStatus());
        assertEquals("CANDIDATE", vo.getEvidenceTrustStatus());
        assertFalse(vo.getCanBeEvidence());
        assertEquals("WAITING_USER_CONFIRMATION", vo.getDisabledReason());
        assertTrue(vo.getImpactPreview().contains("AGENT_TASK"));
    }

    @Test
    void pageMemoriesKeepsEnabledAgentMemoryCandidateWithoutConfirmationSignal() {
        AgentGrowthServiceImpl service = service();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 6, 9, 0);
        AgentMemory candidate = memory(90L, 10L, 1, "AGENT_REVIEW", BigDecimal.valueOf(0.85));
        candidate.setCreatedAt(createdAt);
        candidate.setUpdatedAt(createdAt);
        Page<AgentMemory> page = Page.of(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(candidate));
        when(agentMemoryMapper.selectPage(any(), any())).thenReturn(page);

        AgentMemoryVO vo = service.pageMemories(10L, new AgentMemoryQueryDTO()).getRecords().get(0);

        assertEquals("CANDIDATE", vo.getMemoryStatus());
        assertEquals("CANDIDATE", vo.getEvidenceTrustStatus());
        assertFalse(vo.getCanBeEvidence());
        assertNull(vo.getConfirmedAt());
    }

    @Test
    void pageMemoriesMarksLowConfidenceMemoryAsWeakObservationOnly() {
        AgentGrowthServiceImpl service = service();
        Page<AgentMemory> page = Page.of(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(memory(89L, 10L, 1, "MANUAL", BigDecimal.valueOf(0.4))));
        when(agentMemoryMapper.selectPage(any(), any())).thenReturn(page);

        AgentMemoryVO vo = service.pageMemories(10L, new AgentMemoryQueryDTO()).getRecords().get(0);

        assertEquals("LOW_CONFIDENCE", vo.getMemoryStatus());
        assertEquals("PARTIAL", vo.getEvidenceTrustStatus());
        assertFalse(vo.getCanBeEvidence());
        assertTrue(vo.getLowConfidence());
        assertEquals("LOW_CONFIDENCE", vo.getDisabledReason());
    }

    @Test
    void setMemoryEnabledRejectsOtherUsersMemory() {
        AgentGrowthServiceImpl service = service();
        when(agentMemoryMapper.selectById(99L)).thenReturn(memory(99L, 20L, 1));

        assertThrows(IllegalArgumentException.class, () -> service.setMemoryEnabled(10L, 99L, false));
        verify(agentMemoryMapper, never()).updateById(any(AgentMemory.class));
    }

    @Test
    void setMemoryEnabledUpdatesOwnedMemoryOnly() {
        AgentGrowthServiceImpl service = service();
        when(agentMemoryMapper.selectById(99L))
                .thenReturn(memory(99L, 10L, 1))
                .thenReturn(memory(99L, 10L, 0));

        AgentMemoryVO vo = service.setMemoryEnabled(10L, 99L, false);

        ArgumentCaptor<AgentMemory> memoryCaptor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryMapper).updateById(memoryCaptor.capture());
        assertEquals(0, memoryCaptor.getValue().getEnabled());
        assertEquals(0, vo.getEnabled());
    }

    @Test
    void confirmMemoryPromotesOwnedCandidateToEvidenceWithConfirmationTime() {
        AgentGrowthServiceImpl service = service();
        AgentMemory candidate = memory(99L, 10L, 0, "AGENT_REVIEW", BigDecimal.valueOf(0.85));
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 6, 9, 0);
        candidate.setCreatedAt(createdAt);
        candidate.setUpdatedAt(createdAt);
        AgentMemory confirmed = memory(99L, 10L, 1, "AGENT_REVIEW", BigDecimal.valueOf(0.85));
        confirmed.setCreatedAt(createdAt);
        confirmed.setUpdatedAt(createdAt.plusMinutes(1));
        when(agentMemoryMapper.selectById(99L))
                .thenReturn(candidate)
                .thenReturn(confirmed);

        AgentMemoryVO vo = service.confirmMemory(10L, 99L);

        ArgumentCaptor<AgentMemory> memoryCaptor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryMapper).updateById(memoryCaptor.capture());
        assertEquals(1, memoryCaptor.getValue().getEnabled());
        assertNotNull(memoryCaptor.getValue().getUpdatedAt());
        assertTrue(memoryCaptor.getValue().getUpdatedAt().isAfter(createdAt));
        assertEquals("CONFIRMED", vo.getMemoryStatus());
        assertEquals("VERIFIED", vo.getEvidenceTrustStatus());
        assertTrue(vo.getCanBeEvidence());
        assertNotNull(vo.getConfirmedAt());
    }

    @Test
    void deleteMemoryRejectsOtherUsersMemory() {
        AgentGrowthServiceImpl service = service();
        when(agentMemoryMapper.selectById(99L)).thenReturn(memory(99L, 20L, 1));

        assertThrows(IllegalArgumentException.class, () -> service.deleteMemory(10L, 99L));
        verify(agentMemoryMapper, never()).deleteById(99L);
    }

    @Test
    void deleteMemoryDeletesOwnedMemoryOnly() {
        AgentGrowthServiceImpl service = service();
        when(agentMemoryMapper.selectById(99L)).thenReturn(memory(99L, 10L, 1));

        service.deleteMemory(10L, 99L);

        verify(agentMemoryMapper).deleteById(99L);
    }

    private AgentGrowthServiceImpl service() {
        return new AgentGrowthServiceImpl(
                agentTaskMapper,
                agentRunMapper,
                agentReviewMapper,
                skillGrowthSnapshotMapper,
                readinessScoreRecordMapper,
                agentMemoryMapper,
                new ObjectMapper());
    }

    private AgentMemory memory(Long id, Long userId, Integer enabled) {
        return memory(id, userId, enabled, "MANUAL", BigDecimal.valueOf(0.8));
    }

    private AgentMemory memory(Long id, Long userId, Integer enabled, String sourceType, BigDecimal confidence) {
        AgentMemory memory = new AgentMemory();
        memory.setId(id);
        memory.setUserId(userId);
        memory.setMemoryType("WEAKNESS");
        memory.setContent("Weak area");
        memory.setSourceType(sourceType);
        memory.setConfidence(confidence);
        memory.setEnabled(enabled);
        return memory;
    }

    private AgentTask task(Long id, LocalDate dueDate, String status, String skillCode, String skillName) {
        AgentTask task = new AgentTask();
        task.setId(id);
        task.setUserId(10L);
        task.setDueDate(dueDate);
        task.setStatus(status);
        task.setRelatedSkillCode(skillCode);
        task.setRelatedSkillName(skillName);
        return task;
    }

    private AgentRun run(Long id, LocalDate planDate, String status) {
        AgentRun run = new AgentRun();
        run.setId(id);
        run.setUserId(10L);
        run.setPlanDate(planDate);
        run.setStatus(status);
        return run;
    }

    private SkillGrowthSnapshot skillSnapshot(Long id, LocalDate date, String skillCode, String skillName,
                                              Integer score, Integer taskCount, Integer doneCount) {
        SkillGrowthSnapshot snapshot = new SkillGrowthSnapshot();
        snapshot.setId(id);
        snapshot.setUserId(10L);
        snapshot.setSnapshotDate(date);
        snapshot.setSkillCode(skillCode);
        snapshot.setSkillName(skillName);
        snapshot.setScore(score);
        snapshot.setTaskCount(taskCount);
        snapshot.setDoneCount(doneCount);
        snapshot.setSourceType("AGENT_REVIEW");
        snapshot.setSourceId(100L + id);
        return snapshot;
    }

    private ReadinessScoreRecord readinessRecord(Long id, LocalDate date, Integer score, String evidenceJson) {
        ReadinessScoreRecord record = new ReadinessScoreRecord();
        record.setId(id);
        record.setUserId(10L);
        record.setTargetJobId(99L);
        record.setScoreDate(date);
        record.setScore(score);
        record.setTaskCompletionRate(BigDecimal.valueOf(50));
        record.setAgentSuccessRate(BigDecimal.valueOf(100));
        record.setEvidenceJson(evidenceJson);
        return record;
    }
}
