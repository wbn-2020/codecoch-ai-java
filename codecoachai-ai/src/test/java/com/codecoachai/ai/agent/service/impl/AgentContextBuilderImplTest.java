package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.domain.context.JobApplicationAgentContextVO;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.ApplicationSnapshot;
import com.codecoachai.ai.agent.domain.context.TargetJobContextVO;
import com.codecoachai.ai.agent.domain.entity.AgentMemory;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.feign.ResumeAgentContextFeignClient;
import com.codecoachai.ai.agent.mapper.AgentMemoryMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.common.core.domain.Result;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentContextBuilderImplTest {

    private static final long USER_ID = 10L;
    private static final long TARGET_JOB_ID = 100L;
    private static final LocalDate PLAN_DATE = LocalDate.of(2026, 6, 16);

    @Mock
    private ResumeAgentContextFeignClient resumeFeignClient;
    @Mock
    private AgentTaskMapper agentTaskMapper;
    @Mock
    private AgentMemoryMapper agentMemoryMapper;

    private AgentContextBuilderImpl builder;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(AgentTask.class);
        initTableInfo(AgentMemory.class);
    }

    @BeforeEach
    void setUp() {
        builder = new AgentContextBuilderImpl(resumeFeignClient, agentTaskMapper, agentMemoryMapper);
        when(resumeFeignClient.getTargetJob(USER_ID, TARGET_JOB_ID)).thenReturn(Result.success(targetJob()));
        when(resumeFeignClient.getAnalysis(USER_ID, TARGET_JOB_ID)).thenReturn(Result.success(null));
        when(agentMemoryMapper.selectList(any())).thenReturn(List.of());
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void buildIncludesApplicationSnapshotsFromResumeService() {
        when(resumeFeignClient.listAgentApplications(USER_ID, TARGET_JOB_ID))
                .thenReturn(Result.success(List.of(application(11L))));

        JobCoachAgentContext context = builder.build(USER_ID, TARGET_JOB_ID, PLAN_DATE);

        assertEquals(1, context.getApplications().size());
        ApplicationSnapshot snapshot = context.getApplications().get(0);
        assertEquals(11L, snapshot.getId());
        assertEquals(TARGET_JOB_ID, snapshot.getTargetJobId());
        assertEquals(211L, snapshot.getResumeVersionId());
        assertEquals(511L, snapshot.getResumeId());
        assertEquals(3, snapshot.getResumeVersionNo());
        assertEquals("后端投递版", snapshot.getResumeVersionName());
        assertEquals(1, snapshot.getResumeVersionCurrentFlag());
        assertEquals(311L, snapshot.getMatchReportId());
        assertEquals("CodeCoachAI", snapshot.getCompanyName());
        assertEquals("Java Engineer", snapshot.getJobTitle());
        assertEquals("INTERVIEWING", snapshot.getStatus());
        assertEquals(LocalDateTime.of(2026, 6, 18, 10, 0), snapshot.getNextFollowUpAt());
        assertEquals(Boolean.FALSE, snapshot.getFollowUpOverdue());
        assertEquals(Boolean.TRUE, snapshot.getFollowUpDueToday());
        assertEquals(Long.valueOf(0L), snapshot.getDaysUntilFollowUp());
        assertEquals(711L, snapshot.getLatestEventId());
        assertEquals("INTERVIEW", snapshot.getLatestEventType());
        assertEquals("约定技术面", snapshot.getLatestEventSummary());
        verify(resumeFeignClient).listAgentApplications(USER_ID, TARGET_JOB_ID);
    }

    @Test
    void buildContinuesWhenApplicationContextUnavailable() {
        when(resumeFeignClient.listAgentApplications(USER_ID, TARGET_JOB_ID))
                .thenThrow(new RuntimeException("resume unavailable"));

        JobCoachAgentContext context = builder.build(USER_ID, TARGET_JOB_ID, PLAN_DATE);

        assertTrue(context.getApplications().isEmpty());
        assertTrue(context.getContextWarnings().stream()
                .anyMatch(warning -> warning.contains("投递上下文暂不可用")));
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }

    private TargetJobContextVO targetJob() {
        TargetJobContextVO targetJob = new TargetJobContextVO();
        targetJob.setId(TARGET_JOB_ID);
        targetJob.setUserId(USER_ID);
        targetJob.setJobTitle("Java Engineer");
        targetJob.setCompanyName("CodeCoachAI");
        targetJob.setJobLevel("P6");
        targetJob.setJdSource("manual");
        targetJob.setAnalysisSummary("Spring Boot Redis");
        return targetJob;
    }

    private JobApplicationAgentContextVO application(Long id) {
        JobApplicationAgentContextVO application = new JobApplicationAgentContextVO();
        application.setId(id);
        application.setTargetJobId(TARGET_JOB_ID);
        application.setResumeVersionId(200L + id);
        application.setResumeId(500L + id);
        application.setResumeVersionNo(3);
        application.setResumeVersionName("后端投递版");
        application.setResumeVersionCurrentFlag(1);
        application.setMatchReportId(300L + id);
        application.setCompanyName("CodeCoachAI");
        application.setJobTitle("Java Engineer");
        application.setSource("BOSS");
        application.setStatus("INTERVIEWING");
        application.setAppliedAt(LocalDateTime.of(2026, 6, 10, 9, 0));
        application.setNextFollowUpAt(LocalDateTime.of(2026, 6, 18, 10, 0));
        application.setFollowUpOverdue(false);
        application.setFollowUpDueToday(true);
        application.setDaysUntilFollowUp(0L);
        application.setNote("follow up");
        application.setLatestEventId(700L + id);
        application.setLatestEventType("INTERVIEW");
        application.setLatestEventTime(LocalDateTime.of(2026, 6, 15, 11, 0));
        application.setLatestEventSummary("约定技术面");
        application.setCreatedAt(LocalDateTime.of(2026, 6, 10, 9, 0));
        application.setUpdatedAt(LocalDateTime.of(2026, 6, 15, 9, 0));
        return application;
    }
}
