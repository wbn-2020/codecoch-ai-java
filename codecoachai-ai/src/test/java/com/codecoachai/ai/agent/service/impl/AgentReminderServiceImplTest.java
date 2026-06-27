package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.AgentReminderCandidateVO;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentReminderServiceImplTest {

    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private AgentTaskMapper agentTaskMapper;

    @Test
    void unfinishedTaskReminderUsesTaskBizIdInsteadOfRunId() {
        LocalDate planDate = LocalDate.of(2026, 6, 26);
        AgentRun run = new AgentRun();
        run.setId(42L);
        run.setUserId(7L);
        run.setPlanDate(planDate);
        run.setStatus(AgentRunStatusEnum.SUCCESS.name());

        AgentTask unfinishedTask = new AgentTask();
        unfinishedTask.setId(901L);
        unfinishedTask.setAgentRunId(42L);
        unfinishedTask.setUserId(7L);
        unfinishedTask.setDueDate(planDate);
        unfinishedTask.setTitle("复盘集合题");
        unfinishedTask.setStatus(AgentTaskStatusEnum.TODO.name());
        unfinishedTask.setSortOrder(1);

        when(agentRunMapper.selectOne(any())).thenReturn(run);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(unfinishedTask));

        AgentReminderServiceImpl service = new AgentReminderServiceImpl(agentRunMapper, agentTaskMapper);

        List<AgentReminderCandidateVO> candidates = service.listCandidates(7L, planDate);

        assertEquals(1, candidates.size());
        AgentReminderCandidateVO candidate = candidates.get(0);
        assertEquals("AGENT_TASK", candidate.getBizType());
        assertEquals("901", candidate.getBizId());
        assertEquals("/agent/tasks?bizType=agent.daily-plan.generate&bizId=901", candidate.getActionUrl());
    }

    @Test
    void completedRunReminderUsesRunBizTypeAndRunId() {
        LocalDate planDate = LocalDate.of(2026, 6, 26);
        AgentRun run = new AgentRun();
        run.setId(42L);
        run.setUserId(7L);
        run.setPlanDate(planDate);
        run.setStatus(AgentRunStatusEnum.SUCCESS.name());

        AgentTask doneTask = new AgentTask();
        doneTask.setId(901L);
        doneTask.setAgentRunId(42L);
        doneTask.setUserId(7L);
        doneTask.setDueDate(planDate);
        doneTask.setStatus(AgentTaskStatusEnum.DONE.name());
        doneTask.setSortOrder(1);

        when(agentRunMapper.selectOne(any())).thenReturn(run);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(doneTask));

        AgentReminderServiceImpl service = new AgentReminderServiceImpl(agentRunMapper, agentTaskMapper);

        List<AgentReminderCandidateVO> candidates = service.listCandidates(7L, planDate);

        assertEquals(1, candidates.size());
        AgentReminderCandidateVO candidate = candidates.get(0);
        assertEquals("AGENT_RUN", candidate.getBizType());
        assertEquals("42", candidate.getBizId());
        assertEquals("/agent/runs/42", candidate.getActionUrl());
    }
}
