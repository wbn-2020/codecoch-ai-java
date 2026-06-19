package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.enums.AgentTaskTypeEnum;
import com.codecoachai.ai.service.PromptRenderResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentPromptBuilderImplTest {

    private final AgentPromptBuilderImpl builder = new AgentPromptBuilderImpl(new ObjectMapper());

    @Test
    void buildDailyPlanPromptAllowsApplicationFollowUpCandidateType() {
        CandidateTask followUp = new CandidateTask();
        followUp.setCandidateId("application-follow-up-11");
        followUp.setType(AgentTaskTypeEnum.APPLICATION_FOLLOW_UP.name());
        followUp.setTitle("跟进投递进展");
        followUp.setDescription("查看投递状态并补充沟通记录。");
        followUp.setReason("这条投递今天需要跟进。");
        followUp.setPriority("HIGH");
        followUp.setEstimatedMinutes(15);
        followUp.setRelatedBizType("JOB_APPLICATION");
        followUp.setRelatedBizId(11L);
        followUp.setActionUrl("/applications");

        PromptRenderResult result = builder.buildDailyPlanPrompt(context(), List.of(followUp), 1, 30);

        String prompt = result.getRenderedPrompt();
        assertTrue(prompt.contains("QUESTION_PRACTICE|RESUME_OPTIMIZE|INTERVIEW|SKILL_REVIEW|KNOWLEDGE_REVIEW|APPLICATION_FOLLOW_UP"));
        assertTrue(prompt.contains("\"type\":\"APPLICATION_FOLLOW_UP\""));
        assertTrue(prompt.contains("\"candidateId\":\"application-follow-up-11\""));
    }

    private JobCoachAgentContext context() {
        JobCoachAgentContext context = new JobCoachAgentContext();
        context.setUserId(10L);
        context.setTargetJobId(100L);
        context.setPlanDate(LocalDate.of(2026, 6, 16));
        return context;
    }
}
