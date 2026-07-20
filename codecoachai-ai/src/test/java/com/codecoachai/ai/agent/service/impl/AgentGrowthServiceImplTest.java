package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.agent.domain.dto.AgentMemoryCreateDTO;
import com.codecoachai.ai.agent.domain.dto.AgentMemoryQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentReviewGenerateDTO;
import com.codecoachai.ai.agent.domain.entity.AgentMemory;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
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
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewVO;
import com.codecoachai.ai.agent.mapper.AgentMemoryMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.ReadinessScoreRecordMapper;
import com.codecoachai.ai.agent.mapper.SkillGrowthSnapshotMapper;
import com.codecoachai.ai.agent.service.AgentReviewPlanService;
import com.codecoachai.ai.agent.service.support.AgentBusinessTimeProvider;
import com.codecoachai.ai.domain.dto.GenerateAgentReviewDTO;
import com.codecoachai.ai.domain.vo.GenerateAgentReviewVO;
import com.codecoachai.ai.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentGrowthServiceImplTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(AgentReview.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, AgentReview.class);
        }
    }

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
    @Mock
    private AiService aiService;
    @Mock
    private AgentReviewPlanService agentReviewPlanService;

    @Test
    void generateReviewIsDailyIdempotentAndSkipsAiAndSideEffectsOnRepeat() {
        LocalDate date = LocalDate.of(2026, 7, 18);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                task(1L, date, AgentTaskStatusEnum.TODO.name(), "JAVA", "Java")));
        when(agentRunMapper.selectList(any())).thenReturn(List.of());
        GenerateAgentReviewVO aiReview = aiReview(
                "今日需要继续闭环。",
                List.of("存在一项待处理任务。"),
                List.of("样本有限。"),
                List.of("任务尚未完成。"),
                List.of("缩小下一步动作。"),
                List.of("完成 Java 任务。"));
        when(aiService.generateAgentReview(any())).thenReturn(aiReview);

        AtomicReference<AgentReview> stored = new AtomicReference<>();
        when(agentReviewMapper.selectList(any())).thenAnswer(invocation ->
                stored.get() == null ? List.of() : List.of(stored.get()));
        when(agentReviewMapper.insert(any(AgentReview.class))).thenAnswer(invocation -> {
            AgentReview review = invocation.getArgument(0);
            review.setId(201L);
            stored.set(review);
            return 1;
        });

        AgentReviewGenerateDTO dto = new AgentReviewGenerateDTO();
        dto.setDate(date);

        AgentReviewVO first = service().generateReview(10L, dto);
        AgentReviewVO second = service().generateReview(10L, dto);

        assertEquals(first.getId(), second.getId());
        assertEquals("DAILY", first.getReviewType());
        assertEquals("DAILY:10:2026-07-18:ALL", first.getIdempotencyKey());
        assertNull(first.getSourceTaskId());
        verify(aiService, times(1)).generateAgentReview(any());
        verify(agentReviewMapper, times(1)).insert(any(AgentReview.class));
        verify(readinessScoreRecordMapper, times(1)).insert(any(ReadinessScoreRecord.class));
        verify(skillGrowthSnapshotMapper, times(1)).insert(any(SkillGrowthSnapshot.class));
        verify(agentMemoryMapper, times(1)).insert(any(AgentMemory.class));
    }

    @Test
    void generateReviewUsesDifferentDailyKeysForDifferentJobScopes() {
        LocalDate date = LocalDate.of(2026, 7, 18);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        when(agentRunMapper.selectList(any())).thenReturn(List.of());
        when(aiService.generateAgentReview(any())).thenReturn(aiReview(
                "今日没有任务记录。",
                List.of(),
                List.of("暂无任务样本。"),
                List.of(),
                List.of("补充一项可验证任务。"),
                List.of("补充一项可验证任务。")));
        when(agentReviewMapper.insert(any(AgentReview.class))).thenAnswer(invocation -> {
            AgentReview review = invocation.getArgument(0);
            review.setId(review.getTargetJobId());
            return 1;
        });

        AgentReviewGenerateDTO firstRequest = new AgentReviewGenerateDTO();
        firstRequest.setDate(date);
        firstRequest.setTargetJobId(11L);
        AgentReviewGenerateDTO secondRequest = new AgentReviewGenerateDTO();
        secondRequest.setDate(date);
        secondRequest.setTargetJobId(22L);

        AgentReviewVO first = service().generateReview(10L, firstRequest);
        AgentReviewVO second = service().generateReview(10L, secondRequest);

        assertEquals("DAILY:10:2026-07-18:11", first.getIdempotencyKey());
        assertEquals("DAILY:10:2026-07-18:22", second.getIdempotencyKey());
        verify(aiService, times(2)).generateAgentReview(any());
        verify(agentReviewMapper, times(2)).insert(any(AgentReview.class));
    }

    @Test
    void generateReviewScopesAgentSuccessRateToTargetJob() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        AgentRun successfulRun = run(31L, date, AgentRunStatusEnum.SUCCESS.name());
        successfulRun.setTargetJobId(11L);
        AgentRun otherJobFailedRun = run(32L, date, AgentRunStatusEnum.FAILED.name());
        otherJobFailedRun.setTargetJobId(22L);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                task(1L, date, AgentTaskStatusEnum.DONE.name(), "JAVA", "Java")));
        when(agentRunMapper.selectList(any())).thenReturn(List.of(successfulRun, otherJobFailedRun));
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());
        when(aiService.generateAgentReview(any())).thenReturn(aiReview(
                "Scoped review", List.of(), List.of(), List.of(), List.of(), List.of("Next")));

        AgentReviewGenerateDTO request = new AgentReviewGenerateDTO();
        request.setDate(date);
        request.setTargetJobId(11L);

        service().generateReview(10L, request);

        ArgumentCaptor<GenerateAgentReviewDTO> aiCaptor = ArgumentCaptor.forClass(GenerateAgentReviewDTO.class);
        verify(aiService).generateAgentReview(aiCaptor.capture());
        assertEquals(BigDecimal.valueOf(100).setScale(2), aiCaptor.getValue().getAgentSuccessRate());
    }

    @Test
    void concurrentGenerateReviewCallsAiAndDerivedWritesOnlyOnce() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 20);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                task(1L, date, AgentTaskStatusEnum.TODO.name(), "JAVA", "Java")));
        when(agentRunMapper.selectList(any())).thenReturn(List.of());
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());
        AgentGrowthServiceImpl service = service();
        Object monitor = new Object();
        AtomicBoolean ownerClaimed = new AtomicBoolean();
        AtomicReference<AgentReview> completed = new AtomicReference<>();
        CountDownLatch replayWaiting = new CountDownLatch(1);

        lenient().doAnswer(invocation -> {
                    AgentReview identity = invocation.getArgument(0);
                    synchronized (monitor) {
                        if (ownerClaimed.compareAndSet(false, true)) {
                            identity.setId(601L);
                            identity.setReviewVersion(0);
                            return new AgentReviewPlanService.ReviewGenerationClaim(
                                    identity,
                                    identity.getSourceSnapshotHash(),
                                    0,
                                    identity.getSourceSnapshotHash(),
                                    true,
                                    true);
                        }
                        replayWaiting.countDown();
                        while (completed.get() == null) {
                            monitor.wait(2_000L);
                        }
                        AgentReview current = completed.get();
                        return new AgentReviewPlanService.ReviewGenerationClaim(
                                current,
                                current.getSourceSnapshotHash(),
                                current.getReviewVersion(),
                                current.getSourceSnapshotHash(),
                                false,
                                false);
                    }
                })
                .when(agentReviewPlanService)
                .claimDailyReview(any(AgentReview.class));
        when(aiService.generateAgentReview(any())).thenAnswer(invocation -> {
            assertTrue(replayWaiting.await(5, TimeUnit.SECONDS));
            return aiReview("Concurrent review", List.of(), List.of(), List.of(),
                    List.of("Adjust"), List.of("Next"));
        });
        lenient().doAnswer(invocation -> {
                    AgentReview review = invocation.getArgument(1);
                    review.setReviewVersion(1);
                    completed.set(review);
                    synchronized (monitor) {
                        monitor.notifyAll();
                    }
                    return review;
                })
                .when(agentReviewPlanService)
                .completeClaimedDailyReview(
                        any(AgentReviewPlanService.ReviewGenerationClaim.class),
                        any(AgentReview.class),
                        anyList(),
                        anyList());

        AgentReviewGenerateDTO request = new AgentReviewGenerateDTO();
        request.setDate(date);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AgentReviewVO> first = executor.submit(() -> service.generateReview(10L, request));
            Future<AgentReviewVO> second = executor.submit(() -> service.generateReview(10L, request));

            assertEquals(601L, first.get(10, TimeUnit.SECONDS).getId());
            assertEquals(601L, second.get(10, TimeUnit.SECONDS).getId());
        } finally {
            executor.shutdownNow();
        }

        verify(aiService, times(1)).generateAgentReview(any());
        verify(readinessScoreRecordMapper, times(1)).insert(any(ReadinessScoreRecord.class));
        verify(skillGrowthSnapshotMapper, times(1)).insert(any(SkillGrowthSnapshot.class));
        verify(agentMemoryMapper, times(1)).insert(any(AgentMemory.class));
        InOrder order = inOrder(readinessScoreRecordMapper, skillGrowthSnapshotMapper, agentMemoryMapper);
        order.verify(readinessScoreRecordMapper).insert(any(ReadinessScoreRecord.class));
        order.verify(skillGrowthSnapshotMapper).insert(any(SkillGrowthSnapshot.class));
        order.verify(agentMemoryMapper).insert(any(AgentMemory.class));
    }

    @Test
    void listReviewsAddsDailyTypeFilter() {
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());

        service().listReviews(10L, null);

        ArgumentCaptor<Wrapper<AgentReview>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentReviewMapper).selectList(captor.capture());
        Wrapper<AgentReview> wrapper = captor.getValue();
        assertTrue(wrapper instanceof AbstractWrapper<?, ?, ?>);
        String sqlSegment = ((AbstractWrapper<?, ?, ?>) wrapper).getSqlSegment();
        assertTrue(sqlSegment.contains("review_type"));
        assertTrue(sqlSegment.contains("deleted"));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue("DAILY"));
    }

    @Test
    void generateReviewUsesAiNarrativeAndPersistsStructuredContent() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                task(1L, date, AgentTaskStatusEnum.DONE.name(), "JAVA", "Java"),
                task(2L, date, AgentTaskStatusEnum.DONE.name(), "MYSQL", "MySQL"),
                task(3L, date, AgentTaskStatusEnum.TODO.name(), "JAVA", "Java")));
        when(agentRunMapper.selectList(any())).thenReturn(List.of());
        when(agentReviewMapper.insert(any(AgentReview.class))).thenAnswer(invocation -> {
            AgentReview review = invocation.getArgument(0);
            review.setId(101L);
            return 1;
        });
        GenerateAgentReviewVO aiReview = aiReview(
                "今天完成了两项核心任务，剩余任务需要下一轮继续闭环。",
                List.of("已完成 Java 与 MySQL 两项任务。"),
                List.of("复盘仅基于已记录任务。"),
                List.of("仍有一项任务未闭环。"),
                List.of("将剩余任务拆分为更小步骤。"),
                List.of("优先完成剩余任务。"));
        aiReview.setAiCallLogId(9001L);
        when(aiService.generateAgentReview(any())).thenReturn(aiReview);

        AgentReviewGenerateDTO dto = new AgentReviewGenerateDTO();
        dto.setDate(date);
        AgentReviewVO vo = service().generateReview(10L, dto);

        assertEquals(aiReview.getSummary(), vo.getSummary());
        assertEquals(aiReview.getFacts(), vo.getFacts());
        assertEquals(aiReview.getLimits(), vo.getLimits());
        assertEquals(aiReview.getDriftReasons(), vo.getDriftReasons());
        assertEquals(aiReview.getAdjustments(), vo.getAdjustments());
        assertFalse(vo.getFallback());
        assertEquals("HIGH", vo.getConfidenceLevel());
        assertEquals(9001L, vo.getAiCallLogId());
        assertFalse(vo.getSummary().startsWith("Agent review:"));

        ArgumentCaptor<AgentReview> reviewCaptor = ArgumentCaptor.forClass(AgentReview.class);
        verify(agentReviewMapper).insert(reviewCaptor.capture());
        AgentReview stored = reviewCaptor.getValue();
        assertEquals(Boolean.FALSE, stored.getFallback());
        assertEquals("HIGH", stored.getConfidenceLevel());
        assertTrue(stored.getReviewJson().contains("\"narrative\""));
        assertTrue(stored.getReviewJson().contains("已完成 Java 与 MySQL 两项任务。"));
    }

    @Test
    void generateReviewFallsBackToRulesWhenAiFailsAndKeepsPersistenceChain() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                task(1L, date, AgentTaskStatusEnum.TODO.name(), "JAVA", "Java")));
        when(agentRunMapper.selectList(any())).thenReturn(List.of());
        when(agentReviewMapper.insert(any(AgentReview.class))).thenAnswer(invocation -> {
            AgentReview review = invocation.getArgument(0);
            review.setId(102L);
            return 1;
        });
        when(aiService.generateAgentReview(any())).thenThrow(new RuntimeException("AI unavailable"));

        AgentReviewGenerateDTO dto = new AgentReviewGenerateDTO();
        dto.setDate(date);
        AgentReviewVO vo = service().generateReview(10L, dto);

        assertTrue(vo.getFallback());
        assertEquals("LOW", vo.getConfidenceLevel());
        assertTrue(vo.getFacts().contains("已完成 0 项，已暂缓 0 项，待处理 1 项。"));
        assertTrue(vo.getLimits().contains("任务样本仍较少，当前结论仅作为弱调整信号。"));
        assertEquals(List.of("部分计划动作尚未闭环，下一轮计划应保留或拆分未完成工作。"),
                vo.getDriftReasons());
        assertFalse(vo.getAdjustments().isEmpty());
        assertFalse(vo.getSummary().startsWith("Agent review:"));

        verify(agentReviewMapper).insert(any(AgentReview.class));
        verify(readinessScoreRecordMapper).insert(any(ReadinessScoreRecord.class));
        verify(skillGrowthSnapshotMapper).insert(any(SkillGrowthSnapshot.class));
        verify(agentMemoryMapper).insert(any(AgentMemory.class));
    }

    @Test
    void generateReviewMarksLowConfidenceAndFillsWeakSignalLimitForSmallSample() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                task(1L, date, AgentTaskStatusEnum.DONE.name(), "JAVA", "Java")));
        when(agentRunMapper.selectList(any())).thenReturn(List.of());
        when(agentReviewMapper.insert(any(AgentReview.class))).thenReturn(1);
        GenerateAgentReviewVO aiReview = aiReview(
                "今天完成了一项任务。",
                List.of("已完成一项 Java 任务。"),
                List.of("当前结论无需视为弱调整信号。"),
                List.of(),
                List.of(),
                List.of("记录下一项任务结果。"));
        when(aiService.generateAgentReview(any())).thenReturn(aiReview);

        AgentReviewGenerateDTO dto = new AgentReviewGenerateDTO();
        dto.setDate(date);
        AgentReviewVO vo = service().generateReview(10L, dto);

        assertEquals("LOW", vo.getConfidenceLevel());
        assertFalse(vo.getFallback());
        assertTrue(vo.getLimits().contains("任务样本仍较少，当前结论仅作为弱调整信号。"));
        assertTrue(vo.getLimits().contains("当前结论无需视为弱调整信号。"));
    }

    @Test
    void listReviewsReadsNarrativeFromReviewJsonAfterRefresh() {
        AgentReview review = new AgentReview();
        review.setId(103L);
        review.setUserId(10L);
        review.setReviewDate(LocalDate.of(2026, 7, 17));
        review.setReviewType("DAILY");
        review.setIdempotencyKey("LEGACY:103");
        review.setSummary("已持久化的复盘总结。");
        review.setNextActionsJson("[\"执行下一步\"]");
        review.setReviewJson("""
                {"narrative":{
                  "summary":"已持久化的复盘总结。",
                  "facts":["持久化事实"],
                  "limits":["持久化限制"],
                  "driftReasons":["持久化偏移"],
                  "adjustments":["持久化调整"]
                }}
                """);
        review.setFallback(false);
        review.setConfidenceLevel("HIGH");
        when(agentReviewMapper.selectList(any())).thenReturn(List.of(review));

        AgentReviewVO vo = service().listReviews(10L, null).get(0);

        assertEquals(List.of("持久化事实"), vo.getFacts());
        assertEquals(List.of("持久化限制"), vo.getLimits());
        assertEquals(List.of("持久化偏移"), vo.getDriftReasons());
        assertEquals(List.of("持久化调整"), vo.getAdjustments());
        assertEquals(List.of("执行下一步"), vo.getNextActions());
        assertEquals("DAILY", vo.getReviewType());
        assertEquals("LEGACY:103", vo.getIdempotencyKey());
        assertFalse(vo.getFallback());
        assertEquals("HIGH", vo.getConfidenceLevel());
    }

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
        assertEquals("INPUT_ONLY", vo.getEvidenceTrustStatus());
        assertFalse(vo.getCanBeEvidence());
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
        confirmed.setSourceType("USER_CONFIRMED_AGENT_REVIEW");
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
        assertEquals("INPUT_ONLY", vo.getEvidenceTrustStatus());
        assertFalse(vo.getCanBeEvidence());
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
        lenient().doAnswer(invocation -> {
                    AgentReview review = invocation.getArgument(0);
                    agentReviewMapper.insert(review);
                    review.setReviewVersion(0);
                    return new AgentReviewPlanService.ReviewGenerationClaim(
                            review,
                            review.getSourceSnapshotHash(),
                            0,
                            review.getSourceSnapshotHash(),
                            true,
                            true);
                })
                .when(agentReviewPlanService)
                .claimDailyReview(any(AgentReview.class));
        lenient().doAnswer(invocation -> {
                    AgentReviewPlanService.ReviewGenerationClaim claim = invocation.getArgument(0);
                    AgentReview review = invocation.getArgument(1);
                    review.setReviewVersion(claim.newlyClaimed()
                            ? 1
                            : claim.previousReviewVersion() + 1);
                    return review;
                })
                .when(agentReviewPlanService)
                .completeClaimedDailyReview(
                        any(AgentReviewPlanService.ReviewGenerationClaim.class),
                        any(AgentReview.class),
                        anyList(),
                        anyList());
        lenient().when(agentReviewPlanService.suggestionVOs(any(), any())).thenReturn(List.of());
        return new AgentGrowthServiceImpl(
                agentTaskMapper,
                agentRunMapper,
                agentReviewMapper,
                skillGrowthSnapshotMapper,
                readinessScoreRecordMapper,
                agentMemoryMapper,
                aiService,
                agentReviewPlanService,
                new ObjectMapper(),
                fixedTimeProvider());
    }

    private AgentBusinessTimeProvider fixedTimeProvider() {
        return new AgentBusinessTimeProvider(Clock.fixed(
                Instant.parse("2026-07-20T04:00:00Z"),
                ZoneId.of("Asia/Shanghai")));
    }

    private GenerateAgentReviewVO aiReview(String summary, List<String> facts, List<String> limits,
                                            List<String> driftReasons, List<String> adjustments,
                                            List<String> nextActions) {
        GenerateAgentReviewVO vo = new GenerateAgentReviewVO();
        vo.setSummary(summary);
        vo.setFacts(facts);
        vo.setLimits(limits);
        vo.setDriftReasons(driftReasons);
        vo.setAdjustments(adjustments);
        vo.setNextActions(nextActions);
        return vo;
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
        task.setTitle(skillName + " 任务");
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
