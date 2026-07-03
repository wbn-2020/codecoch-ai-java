package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.ReadinessScoreRecord;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.analytics.ApplicationCareerInsightSummaryVO;
import com.codecoachai.ai.agent.domain.vo.analytics.ApplicationQualityVO;
import com.codecoachai.ai.agent.domain.vo.analytics.CareerInsightOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.CareerRecommendedActionVO;
import com.codecoachai.ai.agent.domain.vo.analytics.InterviewWeaknessInsightVO;
import com.codecoachai.ai.agent.domain.vo.analytics.ResumeVersionEffectItemVO;
import com.codecoachai.ai.agent.domain.vo.analytics.ResumeVersionEffectVO;
import com.codecoachai.ai.agent.domain.vo.analytics.WeaknessInsightItemVO;
import com.codecoachai.ai.agent.feign.InterviewWeaknessInsightFeignClient;
import com.codecoachai.ai.agent.feign.ResumeCareerInsightFeignClient;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.ReadinessScoreRecordMapper;
import com.codecoachai.common.core.domain.Result;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerInsightServiceImplTest {

    @Mock
    private AgentTaskMapper agentTaskMapper;
    @Mock
    private ReadinessScoreRecordMapper readinessScoreRecordMapper;
    @Mock
    private ResumeCareerInsightFeignClient resumeCareerInsightFeignClient;
    @Mock
    private InterviewWeaknessInsightFeignClient interviewWeaknessInsightFeignClient;

    private CareerInsightServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CareerInsightServiceImpl(agentTaskMapper, readinessScoreRecordMapper,
                resumeCareerInsightFeignClient, interviewWeaknessInsightFeignClient);
    }

    @Test
    void downstreamFailureReturnsPartialDataAndWarnings() {
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(doneTask()));
        ReadinessScoreRecord readiness = new ReadinessScoreRecord();
        readiness.setScore(76);
        when(readinessScoreRecordMapper.selectOne(any())).thenReturn(readiness);
        when(resumeCareerInsightFeignClient.careerInsightSummary(10L, 30))
                .thenThrow(new IllegalStateException("resume down"));
        when(interviewWeaknessInsightFeignClient.weaknessSummary(10L, 30))
                .thenReturn(Result.fail(500, "interview down"));

        CareerInsightOverviewVO overview = service.personalCareerInsights(10L, 30);

        assertEquals(30, overview.getRangeDays());
        assertEquals(76, overview.getFunnel().getLatestReadinessScore());
        assertEquals(1L, overview.getFunnel().getAgentTaskDoneCount());
        assertEquals(100D, overview.getFunnel().getAgentTaskCompletionRate());
        assertNotNull(overview.getApplicationQuality());
        assertNotNull(overview.getResumeVersionEffect());
        assertNotNull(overview.getInterviewWeaknesses());
        assertTrue(overview.getDataWarnings().stream().anyMatch(warning -> warning.contains("Resume")));
        assertTrue(overview.getDataWarnings().stream().anyMatch(warning -> warning.contains("Interview")));
    }

    @Test
    void sampleInsufficientDoesNotKeepExaggeratedResumeVersionLabels() {
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        when(readinessScoreRecordMapper.selectOne(any())).thenReturn(null);
        when(resumeCareerInsightFeignClient.careerInsightSummary(10L, 30))
                .thenReturn(Result.success(lowSampleApplicationSummary()));
        when(interviewWeaknessInsightFeignClient.weaknessSummary(10L, 30))
                .thenReturn(Result.success(emptyInterviewSummary()));

        CareerInsightOverviewVO overview = service.personalCareerInsights(10L, 30);

        ResumeVersionEffectItemVO version = overview.getResumeVersionEffect().getVersions().get(0);
        assertEquals("LOW", version.getSampleLevel());
        assertEquals("样本不足", version.getInsightLabel());
        assertFalse(version.getInsightLabel().contains("最好"));
        assertFalse(version.getInsightLabel().contains("最佳"));
        assertFalse(version.getInsightLabel().contains("最差"));
        assertTrue(overview.getDataWarnings().stream().anyMatch(warning -> warning.contains("投递样本不足")));
        assertTrue(overview.getDataWarnings().stream().anyMatch(warning -> warning.contains("简历版本样本不足")));
        assertTrue(overview.getDataWarnings().stream().anyMatch(warning -> warning.contains("面试报告")));
    }

    @Test
    void recommendedActionsFollowRequiredPriorityAndLimitToThree() {
        AgentTask done = doneTask();
        AgentTask todo = todoTask();
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(done, todo, todoTask()));
        ReadinessScoreRecord readiness = new ReadinessScoreRecord();
        readiness.setScore(48);
        when(readinessScoreRecordMapper.selectOne(any())).thenReturn(readiness);
        when(resumeCareerInsightFeignClient.careerInsightSummary(10L, 30))
                .thenReturn(Result.success(riskyApplicationSummary()));
        when(interviewWeaknessInsightFeignClient.weaknessSummary(10L, 30))
                .thenReturn(Result.success(interviewSummaryWithWeakness()));

        CareerInsightOverviewVO overview = service.personalCareerInsights(10L, 30);

        List<CareerRecommendedActionVO> actions = overview.getRecommendedActions();
        assertEquals(3, actions.size());
        assertEquals("OVERDUE_FOLLOW_UP", actions.get(0).getType());
        assertEquals("INTERVIEW_WEAKNESS", actions.get(1).getType());
        assertEquals("RESUME_VERSION_QUALITY", actions.get(2).getType());
    }

    @Test
    void daysAreNormalizedToSupportedRangesAndForwardedToDownstream() {
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        when(readinessScoreRecordMapper.selectOne(any())).thenReturn(null);
        when(resumeCareerInsightFeignClient.careerInsightSummary(any(), any()))
                .thenReturn(Result.success(emptyApplicationSummary()));
        when(interviewWeaknessInsightFeignClient.weaknessSummary(any(), any()))
                .thenReturn(Result.success(emptyInterviewSummary()));

        CareerInsightOverviewVO defaultRange = service.personalCareerInsights(10L, null);
        CareerInsightOverviewVO smallRange = service.personalCareerInsights(10L, 5);
        CareerInsightOverviewVO cappedRange = service.personalCareerInsights(10L, 365);

        assertEquals(30, defaultRange.getRangeDays());
        assertEquals(7, smallRange.getRangeDays());
        assertEquals(90, cappedRange.getRangeDays());
        ArgumentCaptor<Integer> daysCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(resumeCareerInsightFeignClient).careerInsightSummary(10L, 30);
        verify(resumeCareerInsightFeignClient).careerInsightSummary(10L, 7);
        verify(interviewWeaknessInsightFeignClient, times(3)).weaknessSummary(any(), daysCaptor.capture());
        assertTrue(daysCaptor.getAllValues().contains(90));
    }

    private AgentTask doneTask() {
        AgentTask task = new AgentTask();
        task.setUserId(10L);
        task.setStatus(AgentTaskStatusEnum.DONE.name());
        task.setDueDate(LocalDate.now());
        task.setCompletedAt(LocalDateTime.now());
        return task;
    }

    private AgentTask todoTask() {
        AgentTask task = new AgentTask();
        task.setUserId(10L);
        task.setStatus(AgentTaskStatusEnum.TODO.name());
        task.setDueDate(LocalDate.now());
        return task;
    }

    private ApplicationCareerInsightSummaryVO emptyApplicationSummary() {
        ApplicationCareerInsightSummaryVO summary = new ApplicationCareerInsightSummaryVO();
        summary.setApplicationQuality(new ApplicationQualityVO());
        summary.setResumeVersionEffect(new ResumeVersionEffectVO());
        return summary;
    }

    private ApplicationCareerInsightSummaryVO lowSampleApplicationSummary() {
        ApplicationCareerInsightSummaryVO summary = emptyApplicationSummary();
        ApplicationQualityVO quality = new ApplicationQualityVO();
        quality.setTotalApplications(2L);
        quality.setWithResumeVersionCount(1L);
        quality.setWithFollowUpCount(0L);
        summary.setApplicationQuality(quality);

        ResumeVersionEffectVO effect = new ResumeVersionEffectVO();
        effect.setVersionUsedCount(1L);
        effect.setApplicationsWithoutVersionCount(1L);
        ResumeVersionEffectItemVO version = new ResumeVersionEffectItemVO();
        version.setResumeId(1L);
        version.setResumeVersionId(2L);
        version.setApplicationCount(2L);
        version.setInterviewCount(1L);
        version.setInsightLabel("效果最好");
        effect.setVersions(List.of(version));
        summary.setResumeVersionEffect(effect);
        return summary;
    }

    private ApplicationCareerInsightSummaryVO riskyApplicationSummary() {
        ApplicationCareerInsightSummaryVO summary = emptyApplicationSummary();
        ApplicationQualityVO quality = new ApplicationQualityVO();
        quality.setTotalApplications(8L);
        quality.setWithResumeVersionCount(5L);
        quality.setWithFollowUpCount(4L);
        quality.setOverdueFollowUpCount(2L);
        summary.setApplicationQuality(quality);

        ResumeVersionEffectVO effect = new ResumeVersionEffectVO();
        effect.setVersionUsedCount(5L);
        effect.setApplicationsWithoutVersionCount(3L);
        effect.setVersions(List.of());
        summary.setResumeVersionEffect(effect);
        return summary;
    }

    private InterviewWeaknessInsightVO emptyInterviewSummary() {
        InterviewWeaknessInsightVO summary = new InterviewWeaknessInsightVO();
        summary.setInterviewCount(0L);
        summary.setReportCount(0L);
        summary.setTopWeaknesses(List.of());
        return summary;
    }

    private InterviewWeaknessInsightVO interviewSummaryWithWeakness() {
        InterviewWeaknessInsightVO summary = new InterviewWeaknessInsightVO();
        summary.setInterviewCount(2L);
        summary.setReportCount(2L);
        WeaknessInsightItemVO weakness = new WeaknessInsightItemVO();
        weakness.setName("Redis 场景题");
        weakness.setCategory("backend");
        weakness.setCount(2L);
        weakness.setEvidence("最近 2 次报告提到 Redis 场景题");
        weakness.setActionPath("/weakness-analysis");
        summary.setTopWeaknesses(List.of(weakness));
        return summary;
    }
}
