package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.ApplicationSnapshot;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.TargetJobSnapshot;
import com.codecoachai.ai.agent.domain.enums.AgentTaskTypeEnum;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateTaskBuilderImplTest {

    private final CandidateTaskBuilderImpl builder = new CandidateTaskBuilderImpl();

    @Test
    void buildPlacesDueApplicationFollowUpBeforeGenericTraining() {
        ApplicationSnapshot dueToday = application(12L, "INTERVIEWING", false, true,
                LocalDateTime.of(2026, 6, 16, 9, 0));
        ApplicationSnapshot overdue = application(11L, "SUBMITTED", true, false,
                LocalDateTime.of(2026, 6, 15, 9, 0));

        List<CandidateTask> tasks = builder.build(context(List.of(dueToday, overdue)), 3);

        CandidateTask first = tasks.get(0);
        assertEquals(AgentTaskTypeEnum.APPLICATION_FOLLOW_UP.name(), first.getType());
        assertEquals("JOB_APPLICATION", first.getRelatedBizType());
        assertEquals(11L, first.getRelatedBizId());
        assertEquals("/applications", first.getActionUrl());
        assertEquals(15, first.getEstimatedMinutes());
        assertEquals("HIGH", first.getPriority());
        assertTrue(first.getCandidateId().startsWith("application-follow-up-"));
        assertEquals(AgentTaskTypeEnum.APPLICATION_FOLLOW_UP.name(), tasks.get(1).getType());
        assertEquals(15, tasks.get(1).getEstimatedMinutes());
        assertEquals("HIGH", tasks.get(1).getPriority());
        assertEquals(AgentTaskTypeEnum.QUESTION_PRACTICE.name(), tasks.get(2).getType());
    }

    @Test
    void buildDoesNotGenerateApplicationFollowUpWhenApplicationsEmpty() {
        List<CandidateTask> tasks = builder.build(context(List.of()), 3);

        assertTrue(tasks.stream()
                .noneMatch(task -> AgentTaskTypeEnum.APPLICATION_FOLLOW_UP.name().equals(task.getType())));
    }

    @Test
    void buildLimitsApplicationFollowUpCandidatesToTwo() {
        List<CandidateTask> tasks = builder.build(context(List.of(
                application(11L, "SUBMITTED", true, false, LocalDateTime.of(2026, 6, 14, 9, 0)),
                application(12L, "INTERVIEWING", false, true, LocalDateTime.of(2026, 6, 16, 9, 0)),
                application(13L, "INTERVIEWING", false, false, LocalDateTime.of(2026, 6, 20, 9, 0))
        )), 5);

        long applicationTasks = tasks.stream()
                .filter(task -> AgentTaskTypeEnum.APPLICATION_FOLLOW_UP.name().equals(task.getType()))
                .count();
        assertEquals(2, applicationTasks);
    }

    private JobCoachAgentContext context(List<ApplicationSnapshot> applications) {
        JobCoachAgentContext context = new JobCoachAgentContext();
        context.setTargetJobId(100L);
        context.setTargetJob(targetJob());
        context.setApplications(applications);
        return context;
    }

    private TargetJobSnapshot targetJob() {
        TargetJobSnapshot target = new TargetJobSnapshot();
        target.setId(100L);
        target.setJobTitle("Java Backend");
        target.setCompanyName("CodeCoachAI");
        target.setAnalysisSummary("Spring Boot Redis");
        return target;
    }

    private ApplicationSnapshot application(Long id, String status, Boolean overdue, Boolean dueToday,
                                            LocalDateTime nextFollowUpAt) {
        ApplicationSnapshot application = new ApplicationSnapshot();
        application.setId(id);
        application.setTargetJobId(100L);
        application.setResumeVersionId(200L + id);
        application.setMatchReportId(300L + id);
        application.setCompanyName("Company " + id);
        application.setJobTitle("Java Engineer");
        application.setSource("BOSS");
        application.setStatus(status);
        application.setAppliedAt(LocalDateTime.of(2026, 6, 10, 9, 0));
        application.setNextFollowUpAt(nextFollowUpAt);
        application.setFollowUpOverdue(overdue);
        application.setFollowUpDueToday(dueToday);
        application.setDaysUntilFollowUp(overdue ? -1L : 0L);
        application.setNote("follow up");
        application.setCreatedAt(LocalDateTime.of(2026, 6, 10, 9, 0));
        application.setUpdatedAt(LocalDateTime.of(2026, 6, 15, 9, 0));
        return application;
    }
}
