package com.codecoachai.ai.agent.service.support;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentAdaptivePlanHashUtilsTest {

    private static final LocalDate REVIEW_DATE = LocalDate.of(2026, 7, 20);

    @Test
    void reviewSourceHashChangesWhenRunStatusChanges() {
        AgentTask task = task("JAVA", "Java");
        AgentRun running = run("RUNNING");
        AgentRun success = run("SUCCESS");

        String before = AgentAdaptivePlanHashUtils.reviewSourceSnapshotHash(
                10L, 11L, REVIEW_DATE, List.of(task), List.of(running));
        String after = AgentAdaptivePlanHashUtils.reviewSourceSnapshotHash(
                10L, 11L, REVIEW_DATE, List.of(task), List.of(success));

        assertNotEquals(before, after);
    }

    @Test
    void reviewSourceHashChangesWhenSkillInputChanges() {
        AgentTask javaTask = task("JAVA", "Java");
        AgentTask mysqlTask = task("MYSQL", "MySQL");

        String before = AgentAdaptivePlanHashUtils.reviewSourceSnapshotHash(
                10L, 11L, REVIEW_DATE, List.of(javaTask), List.of());
        String after = AgentAdaptivePlanHashUtils.reviewSourceSnapshotHash(
                10L, 11L, REVIEW_DATE, List.of(mysqlTask), List.of());

        assertNotEquals(before, after);
    }

    private AgentTask task(String skillCode, String skillName) {
        AgentTask task = new AgentTask();
        task.setId(101L);
        task.setUserId(10L);
        task.setTargetJobId(11L);
        task.setDueDate(REVIEW_DATE);
        task.setStatus("DONE");
        task.setTaskType("STUDY_TASK");
        task.setTitle("Review task");
        task.setRelatedSkillCode(skillCode);
        task.setRelatedSkillName(skillName);
        return task;
    }

    private AgentRun run(String status) {
        AgentRun run = new AgentRun();
        run.setId(201L);
        run.setUserId(10L);
        run.setTargetJobId(11L);
        run.setPlanDate(REVIEW_DATE);
        run.setAgentType("JOB_COACH");
        run.setStatus(status);
        run.setDeleted(0);
        return run;
    }
}
