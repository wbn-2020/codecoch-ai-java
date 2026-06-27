package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.enums.AgentTaskTypeEnum;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentPromptBuilderImplTest {

    private final AgentPromptBuilderImpl builder =
            new AgentPromptBuilderImpl(objectMapper(), new FallbackPromptRenderService());

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

    @Test
    void buildDailyPlanPromptDelegatesToPromptRenderServiceAndPropagatesTemplateVersion() throws Exception {
        CapturingPromptRenderService renderService = new CapturingPromptRenderService();
        AgentPromptBuilderImpl governedBuilder = newBuilderWithPromptRenderService(renderService);

        PromptRenderResult result = governedBuilder.buildDailyPlanPrompt(context(), List.of(followUpCandidate()), 1, 30);

        assertEquals(AgentPromptBuilderImpl.PROMPT_TYPE, renderService.scene);
        assertTrue(renderService.fallbackContent.contains("APPLICATION_FOLLOW_UP"));
        assertEquals("1", renderService.variables.get("taskCount"));
        assertEquals("30", renderService.variables.get("maxTotalMinutes"));
        assertTrue(renderService.variables.get("contextJson").contains("\"userId\":10"));
        assertTrue(renderService.variables.get("candidatesJson").contains("application-follow-up-11"));
        assertEquals(88L, result.getPromptTemplateVersionId());
        assertFalse(result.getFallbackUsed());
        assertTrue(result.getRenderedPrompt().contains("managed prompt"));
    }

    private AgentPromptBuilderImpl newBuilderWithPromptRenderService(PromptRenderService renderService) throws Exception {
        try {
            return new AgentPromptBuilderImpl(objectMapper(), renderService);
        } catch (NoSuchMethodError ex) {
            fail("AgentPromptBuilderImpl should delegate through PromptRenderService for PromptTemplate governance");
            return null;
        }
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private CandidateTask followUpCandidate() {
        CandidateTask followUp = new CandidateTask();
        followUp.setCandidateId("application-follow-up-11");
        followUp.setType(AgentTaskTypeEnum.APPLICATION_FOLLOW_UP.name());
        followUp.setTitle("Application follow-up");
        followUp.setDescription("Check application status and record communication.");
        followUp.setReason("This application needs a timely follow-up today.");
        followUp.setPriority("HIGH");
        followUp.setEstimatedMinutes(15);
        followUp.setRelatedBizType("JOB_APPLICATION");
        followUp.setRelatedBizId(11L);
        followUp.setActionUrl("/applications");
        return followUp;
    }

    private JobCoachAgentContext context() {
        JobCoachAgentContext context = new JobCoachAgentContext();
        context.setUserId(10L);
        context.setTargetJobId(100L);
        context.setPlanDate(LocalDate.of(2026, 6, 16));
        return context;
    }

    private static class CapturingPromptRenderService implements PromptRenderService {

        private String scene;
        private String fallbackContent;
        private Map<String, String> variables = new LinkedHashMap<>();

        @Override
        public PromptRenderResult render(String scene, String fallbackContent, Map<String, String> variables) {
            this.scene = scene;
            this.fallbackContent = fallbackContent;
            this.variables = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
            return PromptRenderResult.builder()
                    .scene(scene)
                    .renderedPrompt("managed prompt for " + variables.get("taskCount"))
                    .promptTemplateId(77L)
                    .promptTemplateVersionId(88L)
                    .promptVersion("agent-template-v1")
                    .inputVariablesJson("{}")
                    .modelParamsJson("{\"temperature\":0.2}")
                    .promptHash("hash")
                    .fallbackUsed(false)
                    .build();
        }

        @Override
        public PromptRenderResult render(String scene, String fallbackContent, Map<String, String> variables,
                                         String prefix, String suffix) {
            return render(scene, fallbackContent, variables);
        }
    }

    private static class FallbackPromptRenderService implements PromptRenderService {

        @Override
        public PromptRenderResult render(String scene, String fallbackContent, Map<String, String> variables) {
            return PromptRenderResult.builder()
                    .scene(scene)
                    .renderedPrompt(fallbackContent)
                    .promptVersion("BUILTIN")
                    .inputVariablesJson("{}")
                    .promptHash("fallback-hash")
                    .fallbackUsed(true)
                    .build();
        }

        @Override
        public PromptRenderResult render(String scene, String fallbackContent, Map<String, String> variables,
                                         String prefix, String suffix) {
            return render(scene, fallbackContent, variables);
        }
    }
}
