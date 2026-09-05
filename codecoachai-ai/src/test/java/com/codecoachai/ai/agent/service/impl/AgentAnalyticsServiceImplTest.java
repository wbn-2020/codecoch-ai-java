package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.analytics.AdminAgentOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.MetricPointVO;
import com.codecoachai.ai.agent.domain.vo.analytics.TrendPointVO;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentAnalyticsServiceImplTest {

    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private AgentTaskMapper agentTaskMapper;
    @Mock
    private AiCallLogMapper aiCallLogMapper;

    private AgentAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableInfo(AgentRun.class);
        initializeTableInfo(AgentTask.class);
        service = new AgentAnalyticsServiceImpl(agentRunMapper, agentTaskMapper, aiCallLogMapper);
    }

    @Test
    void adminOverviewSeparatesFullSuccessFromDegradedSuccess() {
        when(agentRunMapper.selectList(any())).thenReturn(List.of(
                run(AgentRunStatusEnum.SUCCESS.name(), "MODEL"),
                run(AgentRunStatusEnum.SUCCESS.name(), "FALLBACK"),
                run(AgentRunStatusEnum.FAILED.name(), null)
        ));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        AdminAgentOverviewVO overview = service.adminAgentOverview(7);

        assertEquals(3L, overview.getTotalAgentRuns());
        assertEquals(2L, overview.getSuccessAgentRuns());
        assertEquals(1L, overview.getDegradedAgentRuns());
        assertEquals(2L, overview.getEffectiveAgentRuns());
        assertEquals(33.33D, overview.getAgentSuccessRate());
        assertEquals(66.67D, overview.getEffectiveSuccessRate());
    }

    @Test
    void taskTrendReportsEstimatedMinutesForCompletedTasksOnly() {
        LocalDate today = LocalDate.now();
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                task(today, AgentTaskStatusEnum.DONE.name(), 30, "Java", null),
                task(today, AgentTaskStatusEnum.SKIPPED.name(), 20, "Redis", null),
                task(today, AgentTaskStatusEnum.TODO.name(), 10, "MySQL", null)
        ));

        List<TrendPointVO> trend = service.personalTaskTrend(7L, 1);

        assertEquals(1, trend.size());
        assertEquals(60L, trend.get(0).getEstimatedMinutes());
        assertEquals(30L, trend.get(0).getCompletedMinutes());
    }

    @Test
    void skillDistributionIncludesOnlyCompletedTasksWithExplicitSkills() {
        LocalDate today = LocalDate.now();
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                task(today, AgentTaskStatusEnum.DONE.name(), 30, "Java", null),
                task(today, AgentTaskStatusEnum.DONE.name(), 20, null, "REDIS"),
                task(today, AgentTaskStatusEnum.DONE.name(), 15, " ", " "),
                task(today, AgentTaskStatusEnum.TODO.name(), 10, "MySQL", null),
                task(today, AgentTaskStatusEnum.SKIPPED.name(), 10, "Spring", null)
        ));

        List<MetricPointVO> distribution = service.personalSkillDistribution(7L, 7);

        assertEquals(2, distribution.size());
        assertEquals("Java", distribution.get(0).getName());
        assertEquals(1L, distribution.get(0).getValue());
        assertEquals("REDIS", distribution.get(1).getName());
        assertEquals(1L, distribution.get(1).getValue());
    }

    @Test
    void personalAnalyticsPropagatesStorageFailuresInsteadOfReportingEmptyMetrics() {
        when(agentTaskMapper.selectList(any())).thenThrow(new RuntimeException("agent task table unavailable"));

        assertThrows(RuntimeException.class, () -> service.personalOverview(7L));
        assertThrows(RuntimeException.class, () -> service.personalTaskTrend(7L, 1));
        assertThrows(RuntimeException.class, () -> service.personalSkillDistribution(7L, 7));

        reset(agentTaskMapper, agentRunMapper);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        when(agentRunMapper.selectList(any())).thenThrow(new RuntimeException("agent run table unavailable"));
        assertThrows(RuntimeException.class, () -> service.personalOverview(7L));
    }

    private static AgentRun run(String status, String resultSource) {
        AgentRun run = new AgentRun();
        run.setStatus(status);
        run.setResultSource(resultSource);
        run.setDurationMs(100L);
        return run;
    }

    private static AgentTask task(LocalDate dueDate, String status, Integer estimatedMinutes,
                                  String relatedSkillName, String relatedSkillCode) {
        AgentTask task = new AgentTask();
        task.setDueDate(dueDate);
        task.setStatus(status);
        task.setEstimatedMinutes(estimatedMinutes);
        task.setRelatedSkillName(relatedSkillName);
        task.setRelatedSkillCode(relatedSkillCode);
        return task;
    }

    private static void initializeTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    entityType
            );
        }
    }
}
