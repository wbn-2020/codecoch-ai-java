package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.vo.analytics.AdminAgentOverviewVO;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.mapper.AiCallLogMapper;
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

    private static AgentRun run(String status, String resultSource) {
        AgentRun run = new AgentRun();
        run.setStatus(status);
        run.setResultSource(resultSource);
        run.setDurationMs(100L);
        return run;
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
