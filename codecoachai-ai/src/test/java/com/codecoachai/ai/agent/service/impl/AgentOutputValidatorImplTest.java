package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult.PlanTask;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentOutputValidatorImplTest {
    private final AgentOutputValidatorImpl validator = new AgentOutputValidatorImpl();

    @Test
    void acceptsTechnicalSubjectsAndNormalizesOnlySafeFormatting() {
        PlanTask task = task(" c1 ");
        task.setType(" question_practice ");
        task.setPriority(" high ");
        task.setTitle("练习 Java Backend、DeepSeek、DTO 和 REST API 后端接口设计");
        validator.validateDailyPlan(plan(task), List.of(candidate("c1")), 3, 120);
        assertEquals("c1", task.getCandidateId());
        assertEquals("QUESTION_PRACTICE", task.getType());
        assertEquals("HIGH", task.getPriority());
    }

    @Test
    void modelCannotSupplyMissingCandidateMetadata() {
        PlanTask task = task("c1");
        task.setRelatedBizType("ADMIN_USER");
        task.setRelatedBizId(999L);
        task.setRelatedSkillCode("invented");
        task.setRelatedSkillName("invented");
        task.setActionUrl("/admin/users");
        CandidateTask candidate = candidate("c1");
        candidate.setActionUrl(null);
        validator.validateDailyPlan(plan(task), List.of(candidate), 3, 120);
        assertNull(task.getRelatedBizType());
        assertNull(task.getRelatedBizId());
        assertNull(task.getRelatedSkillCode());
        assertNull(task.getRelatedSkillName());
        assertNull(task.getActionUrl());
    }

    @Test
    void unknownIdDoesNotMatchByTitleOrCase() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateDailyPlan(plan(task("C1")), List.of(candidate("c1")), 3, 120));
        assertEquals("UNKNOWN_CANDIDATE", ex.getFieldErrors().get("tasks[0].candidateId"));
        assertFalse(ex.getFieldErrors().toString().contains("C1"));
    }

    @Test
    void rejectsRepeatedCandidateEvenWithDifferentTitle() {
        PlanTask second = task("c1");
        second.setTitle("另一个任务标题");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateDailyPlan(plan(task("c1"), second), List.of(candidate("c1")), 3, 120));
        assertEquals("DUPLICATE_CANDIDATE", ex.getFieldErrors().get("tasks[1].candidateId"));
    }

    @Test
    void rejectsCandidateTypeMismatch() {
        PlanTask task = task("c1");
        task.setType("INTERVIEW");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateDailyPlan(plan(task), List.of(candidate("c1")), 3, 120));
        assertEquals("CANDIDATE_TYPE_MISMATCH", ex.getFieldErrors().get("tasks[0].type"));
    }

    @Test
    void reportsTaskAndTimeLimitsWithoutOutputContent() {
        assertRule("TASK_COUNT", () -> validator.validateDailyPlan(
                plan(task("c1"), task("c2")), List.of(candidate("c1"), candidate("c2")), 1, 120));
        assertRule("TOTAL_MINUTES", () -> validator.validateDailyPlan(
                plan(task("c1")), List.of(candidate("c1")), 3, 20));
        PlanTask task = task("c1");
        task.setEstimatedMinutes(4);
        assertRule("TASK_MINUTES", () -> validator.validateDailyPlan(
                plan(task), List.of(candidate("c1")), 3, 120));
    }

    @Test
    void stillRejectsEnglishOnlyAndInternalRuntimeText() {
        PlanTask task = task("c1");
        task.setDescription("Practice questions");
        assertRule("CHINESE_REQUIRED", () -> validator.validateDailyPlan(
                plan(task), List.of(candidate("c1")), 3, 120));
        task.setDescription("查看 candidateId 内部字段");
        assertRule("INTERNAL_TEXT", () -> validator.validateDailyPlan(
                plan(task), List.of(candidate("c1")), 3, 120));
    }

    static CandidateTask candidate(String id) {
        CandidateTask candidate = new CandidateTask();
        candidate.setCandidateId(id);
        candidate.setType("QUESTION_PRACTICE");
        candidate.setTitle("练习专项题目" + id);
        candidate.setDescription("完成练习并记录问题");
        candidate.setReason("补强当前岗位要求");
        candidate.setPriority("HIGH");
        candidate.setEstimatedMinutes(30);
        candidate.setActionUrl("/questions/practice");
        return candidate;
    }

    static PlanTask task(String id) {
        PlanTask task = new PlanTask();
        task.setCandidateId(id);
        task.setType("QUESTION_PRACTICE");
        task.setTitle("练习专项题目" + id);
        task.setDescription("完成练习并记录问题");
        task.setReason("补强当前岗位要求");
        task.setPriority("HIGH");
        task.setEstimatedMinutes(30);
        return task;
    }

    static DailyPlanResult plan(PlanTask... tasks) {
        DailyPlanResult result = new DailyPlanResult();
        result.setSummary("今日训练计划");
        result.setTasks(List.of(tasks));
        return result;
    }

    private void assertRule(String rule, org.junit.jupiter.api.function.Executable action) {
        BusinessException ex = assertThrows(BusinessException.class, action);
        assertTrue(ex.getFieldErrors().containsValue(rule));
    }
}
