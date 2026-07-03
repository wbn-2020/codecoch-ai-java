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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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

    @Test
    void applicationFollowUpReasonUsesLatestEventAndResumeVersionEvidence() {
        ApplicationSnapshot app = application(11L, "INTERVIEWING", true, false,
                LocalDateTime.of(2026, 6, 14, 9, 0));
        app.setLatestEventSummary("HR 已确认进入技术面");
        app.setResumeVersionName("后端投递版");

        List<CandidateTask> tasks = builder.build(context(List.of(app)), 2);

        CandidateTask followUp = tasks.get(0);
        assertTrue(followUp.getReason().contains("最近记录：HR 已确认进入技术面"));
        assertTrue(followUp.getReason().contains("关联简历版本：后端投递版"));
    }

    @Test
    void buildCreatesJobExperimentTaskWhenExperimentNeedsNextAction() throws Exception {
        JobCoachAgentContext context = context(List.of());
        Object experiment = jobExperimentSnapshot();
        Method setExperiments = JobCoachAgentContext.class.getMethod("setJobExperiments", List.class);
        setExperiments.invoke(context, List.of(experiment));

        List<CandidateTask> tasks = builder.build(context, 3);

        CandidateTask experimentTask = tasks.stream()
                .filter(task -> "JOB_EXPERIMENT".equals(task.getRelatedBizType()))
                .findFirst()
                .orElseThrow();
        assertEquals("JOB_EXPERIMENT", experimentTask.getRelatedBizType());
        assertEquals(7L, experimentTask.getRelatedBizId());
        assertEquals("/job-experiments/7", experimentTask.getActionUrl());
        assertTrue(experimentTask.getReason().contains("样本不足"));
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
        application.setResumeId(500L + id);
        application.setResumeVersionNo(3);
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

    private Object jobExperimentSnapshot() throws Exception {
        Class<?> type = Class.forName("com.codecoachai.ai.agent.domain.context.JobCoachAgentContext$JobExperimentSnapshot");
        Constructor<?> constructor = type.getDeclaredConstructor();
        Object snapshot = constructor.newInstance();
        type.getMethod("setId", Long.class).invoke(snapshot, 7L);
        type.getMethod("setTitle", String.class).invoke(snapshot, "Redis 方向投递实验");
        type.getMethod("setTargetDirection", String.class).invoke(snapshot, "Java 后端 / Redis");
        type.getMethod("setSampleCount", Integer.class).invoke(snapshot, 3);
        type.getMethod("setConfidenceLevel", String.class).invoke(snapshot, "LOW");
        type.getMethod("setSampleWarning", String.class).invoke(snapshot, "样本不足：投递少于 5 条。");
        type.getMethod("setNextStrategy", String.class).invoke(snapshot, "继续积累可比较投递。");
        return snapshot;
    }
}
