package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.domain.entity.AgentContextUsageReference;
import com.codecoachai.ai.agent.domain.entity.AgentPlanAdjustment;
import com.codecoachai.ai.agent.domain.entity.AgentPlanInfluence;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlan;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlanItem;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentWeekPlanVO;
import com.codecoachai.ai.agent.feign.ResumeAgentContextFeignClient;
import com.codecoachai.ai.agent.mapper.AgentContextUsageReferenceMapper;
import com.codecoachai.ai.agent.mapper.AgentPlanAdjustmentMapper;
import com.codecoachai.ai.agent.mapper.AgentPlanInfluenceMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanItemMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentWeekPlanServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private AgentWeekPlanMapper weekPlanMapper;
    @Mock
    private AgentWeekPlanItemMapper weekPlanItemMapper;
    @Mock
    private AgentPlanAdjustmentMapper adjustmentMapper;
    @Mock
    private AgentPlanInfluenceMapper influenceMapper;
    @Mock
    private AgentTaskMapper agentTaskMapper;
    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private AgentContextUsageReferenceMapper usageReferenceMapper;
    @Mock
    private ResumeAgentContextFeignClient resumeAgentContextFeignClient;

    private AgentWeekPlanServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(AgentTask.class);
        initTableInfo(AgentWeekPlan.class);
        initTableInfo(AgentWeekPlanItem.class);
        initTableInfo(AgentPlanInfluence.class);
        initTableInfo(AgentContextUsageReference.class);
        initTableInfo(AgentPlanAdjustment.class);
        initTableInfo(AgentRun.class);
    }

    @BeforeEach
    void setUp() {
        service = new AgentWeekPlanServiceImpl(
                weekPlanMapper,
                weekPlanItemMapper,
                adjustmentMapper,
                influenceMapper,
                agentTaskMapper,
                agentRunMapper,
                usageReferenceMapper,
                resumeAgentContextFeignClient,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void currentRepairsAnExistingPlanWithItemsOutsideItsWeek() {
        AgentWeekPlan plan = plan();
        when(weekPlanMapper.selectOne(any())).thenReturn(plan);
        when(weekPlanItemMapper.selectCount(any())).thenReturn(1L);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        when(weekPlanItemMapper.selectList(any())).thenReturn(List.of());
        when(weekPlanMapper.updateById(any(AgentWeekPlan.class))).thenReturn(1);

        AgentWeekPlanVO result = service.current(USER_ID, null, LocalDate.of(2026, 7, 15));

        assertEquals("REFRESHED", result.getPlanStatus());
        assertEquals(2, result.getSnapshotVersion());
        verify(weekPlanMapper).updateById(plan);
        verify(weekPlanItemMapper).delete(any());
    }

    @Test
    void currentKeepsAnExistingPlanWhenItsItemsAreWithinTheWeek() {
        AgentWeekPlan plan = plan();
        when(weekPlanMapper.selectOne(any())).thenReturn(plan);
        when(weekPlanItemMapper.selectCount(any())).thenReturn(0L);
        when(weekPlanItemMapper.selectList(any())).thenReturn(List.of());

        AgentWeekPlanVO result = service.current(USER_ID, null, LocalDate.of(2026, 7, 15));

        assertEquals("ACTIVE", result.getPlanStatus());
        verify(weekPlanMapper, never()).updateById(any(AgentWeekPlan.class));
    }

    @Test
    void weekTaskQueryOnlyIncludesUndatedOpenTasksOutsideTheSelectedWeek() throws Exception {
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        ArgumentCaptor<Wrapper<AgentTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);

        Method method = AgentWeekPlanServiceImpl.class.getDeclaredMethod(
                "weekTasks", Long.class, Long.class, LocalDate.class, LocalDate.class);
        method.setAccessible(true);
        method.invoke(service, USER_ID, null, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19));

        verify(agentTaskMapper).selectList(wrapperCaptor.capture());
        String sql = ((AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue()).getSqlSegment().toUpperCase();
        assertTrue(sql.contains("DUE_DATE BETWEEN"));
        assertTrue(sql.contains("DUE_DATE IS NULL"));
        assertFalse(sql.contains("OR STATUS IN"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void responseEvidenceOmitsInternalTaskRunTraceAndHashMetadata() throws Exception {
        Method method = AgentWeekPlanServiceImpl.class.getDeclaredMethod("parseEvidence", String.class);
        method.setAccessible(true);

        List<String> evidence = (List<String>) method.invoke(
                service,
                "{\"agentTaskId\":42,\"agentRunId\":10,\"traceId\":\"trace-1\","
                        + "\"relatedBizType\":\"TARGET_JOB\",\"titleHash\":\"hash\"}");

        assertEquals(List.of("来源：目标岗位"), evidence);
    }

    @Test
    void generatedWeekPlanCopyUsesTaskContentAndChineseFallbacks() throws Exception {
        AgentTask task = new AgentTask();
        task.setTitle("完成系统设计专项复练");
        task.setDescription("围绕容量估算、限流降级和缓存一致性完成练习。");
        task.setReason("最近报告显示系统设计表达仍需加强。");
        task.setRelatedBizType("INTERVIEW_REPORT");

        assertEquals("完成系统设计专项复练", invokeTaskText("safeWeekPlanTitle", task));
        assertEquals("围绕容量估算、限流降级和缓存一致性完成练习。", invokeTaskText("safeWeekPlanDescription", task));
        assertEquals("最近报告显示系统设计表达仍需加强。", invokeTaskText("safeDefaultReason", task));

        AgentTask fallbackTask = new AgentTask();
        fallbackTask.setRelatedBizType("QUESTION");
        assertEquals("题库练习", invokeTaskText("safeWeekPlanTitle", fallbackTask));
        assertEquals("请结合关联记录完成本项训练或复核。", invokeTaskText("safeWeekPlanDescription", fallbackTask));
        assertEquals("该任务已根据当前准备状态生成，请结合关联记录推进。", invokeTaskText("safeDefaultReason", fallbackTask));
    }

    private AgentWeekPlan plan() {
        AgentWeekPlan plan = new AgentWeekPlan();
        plan.setId(5L);
        plan.setUserId(USER_ID);
        plan.setWeekStartDate(LocalDate.of(2026, 7, 13));
        plan.setWeekEndDate(LocalDate.of(2026, 7, 19));
        plan.setPlanStatus("ACTIVE");
        plan.setSnapshotVersion(1);
        plan.setDeleted(0);
        return plan;
    }

    private String invokeTaskText(String methodName, AgentTask task) throws Exception {
        Method method = AgentWeekPlanServiceImpl.class.getDeclaredMethod(methodName, AgentTask.class);
        method.setAccessible(true);
        return (String) method.invoke(service, task);
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(entityClass.getName() + "Mapper");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
