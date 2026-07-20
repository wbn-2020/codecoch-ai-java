package com.codecoachai.ai.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.domain.dto.AgentPlanSuggestionIntentDTO;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanSuggestion;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.service.support.AgentPlanChangeJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentPlanPreviewPlannerTest {

    private AgentPlanChangeJsonCodec jsonCodec;
    private AgentPlanPreviewPlanner planner;

    @BeforeEach
    void setUp() {
        jsonCodec = new AgentPlanChangeJsonCodec(new ObjectMapper().findAndRegisterModules());
        planner = new AgentPlanPreviewPlanner(jsonCodec, new AgentPlanPreviewValidator());
    }

    @Test
    void lowConfidenceCarryOverIsLimitedToOneThirtyMinuteNonHighTask() {
        AgentReview review = review("LOW", true);
        AgentTask source = task(101L, LocalDate.of(2026, 7, 18), "HIGH", 90);
        AgentReviewPlanSuggestion suggestion = suggestion(301L, "CARRY_OVER", source.getId(), "LOW", true);

        AgentPlanPreviewPlanner.PlanningResult result = planner.plan(
                new AgentPlanPreviewPlanner.PlanningInput(
                        review,
                        List.of(suggestion),
                        List.of(source),
                        List.of(source),
                        List.of(),
                        null,
                        null,
                        List.of(),
                        LocalDate.of(2026, 7, 19),
                        120,
                        LocalDate.of(2026, 7, 18)));

        assertEquals(1, result.items().size());
        assertEquals(30, result.items().get(0).after().getEstimatedMinutes());
        assertEquals("MEDIUM", result.items().get(0).after().getPriority());
        assertEquals("CARRY_OVER_TASK", result.items().get(0).changeType());
        assertTrue(result.warnings().contains(AgentPlanPreviewValidator.WARNING_LOW_CONFIDENCE));
        assertTrue(result.blockers().isEmpty());
    }

    @Test
    void lowConfidenceReviewCannotRemoveAnOpenTask() {
        AgentReview review = review("LOW", false);
        AgentTask target = task(102L, LocalDate.of(2026, 7, 19), "LOW", 30);
        AgentReviewPlanSuggestion suggestion = suggestion(302L, "REDUCE_LOAD", target.getId(), "LOW", false);

        AgentPlanPreviewPlanner.PlanningResult result = planner.plan(
                new AgentPlanPreviewPlanner.PlanningInput(
                        review,
                        List.of(suggestion),
                        List.of(target),
                        List.of(target),
                        List.of(target),
                        null,
                        null,
                        List.of(),
                        LocalDate.of(2026, 7, 19),
                        120,
                        LocalDate.of(2026, 7, 18)));

        assertTrue(result.items().isEmpty());
        assertFalse(result.blockers().isEmpty());
        assertTrue(result.blockers().stream().anyMatch(message -> message.contains("不允许移出任务")));
    }

    @Test
    void rescheduleMovesOpenTaskToDifferentAllowedDate() {
        AgentReview review = review("HIGH", false);
        AgentTask source = task(103L, LocalDate.of(2026, 7, 18), "MEDIUM", 30);
        AgentReviewPlanSuggestion suggestion = suggestion(303L, "RESCHEDULE", source.getId(), "HIGH", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(), LocalDate.of(2026, 7, 19));

        assertEquals(1, result.items().size());
        assertEquals("RESCHEDULE_TASK", result.items().get(0).changeType());
        assertEquals(LocalDate.of(2026, 7, 18), result.items().get(0).before().getDueDate());
        assertEquals(LocalDate.of(2026, 7, 19), result.items().get(0).after().getDueDate());
        assertTrue(result.blockers().isEmpty());
    }

    @Test
    void rescheduleRejectsSameSourceAndTargetDate() {
        AgentReview review = review("HIGH", false);
        AgentTask source = task(104L, LocalDate.of(2026, 7, 19), "MEDIUM", 30);
        AgentReviewPlanSuggestion suggestion = suggestion(304L, "RESCHEDULE", source.getId(), "HIGH", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(source), LocalDate.of(2026, 7, 19));

        assertTrue(result.items().isEmpty());
        assertTrue(result.blockers().stream().anyMatch(message -> message.contains("目标日期与原日期相同")));
    }

    @Test
    void rescheduleRejectsTargetDateBeforeSourceDueDate() {
        AgentReview review = review("HIGH", false);
        AgentTask source = task(111L, LocalDate.of(2026, 7, 19), "MEDIUM", 30);
        AgentReviewPlanSuggestion suggestion = suggestion(311L, "RESCHEDULE", source.getId(), "HIGH", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(), LocalDate.of(2026, 7, 18));

        assertTrue(result.items().isEmpty());
        assertTrue(result.blockers().stream().anyMatch(message -> message.contains("必须晚于原日期")));
    }

    @Test
    void priorityChangeSucceedsForTaskOnPreviewTargetDate() {
        AgentReview review = review("HIGH", false);
        AgentTask source = task(105L, LocalDate.of(2026, 7, 19), "MEDIUM", 30);
        AgentReviewPlanSuggestion suggestion = prioritySuggestion(
                305L, source.getId(), "HIGH", "HIGH", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(source), LocalDate.of(2026, 7, 19));

        assertEquals(1, result.items().size());
        assertEquals("CHANGE_PRIORITY", result.items().get(0).changeType());
        assertEquals("HIGH", result.items().get(0).after().getPriority());
        assertTrue(result.blockers().isEmpty());
    }

    @Test
    void priorityChangeRejectsTaskOutsidePreviewTargetDate() {
        AgentReview review = review("HIGH", false);
        AgentTask source = task(106L, LocalDate.of(2026, 7, 18), "MEDIUM", 30);
        AgentReviewPlanSuggestion suggestion = prioritySuggestion(
                306L, source.getId(), "HIGH", "HIGH", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(), LocalDate.of(2026, 7, 19));

        assertTrue(result.items().isEmpty());
        assertTrue(result.blockers().stream().anyMatch(message -> message.contains("目标日期内任务的优先级")));
    }

    @Test
    void weakRescheduleBlocksHardDeadlineTask() {
        AgentReview review = review("LOW", false);
        AgentTask source = hardDeadlineTask(107L, LocalDate.of(2026, 7, 18), "MEDIUM");
        AgentReviewPlanSuggestion suggestion = suggestion(307L, "RESCHEDULE", source.getId(), "LOW", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(), LocalDate.of(2026, 7, 19));

        assertTrue(result.items().isEmpty());
        assertTrue(result.blockers().stream().anyMatch(message -> message.contains("不允许调整有明确面试")));
    }

    @Test
    void normalRescheduleWarnsForHardDeadlineTask() {
        AgentReview review = review("HIGH", false);
        AgentTask source = hardDeadlineTask(108L, LocalDate.of(2026, 7, 18), "MEDIUM");
        AgentReviewPlanSuggestion suggestion = suggestion(308L, "RESCHEDULE", source.getId(), "HIGH", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(), LocalDate.of(2026, 7, 19));

        assertEquals(1, result.items().size());
        assertTrue(result.items().get(0).warningCodes()
                .contains(AgentPlanPreviewValidator.WARNING_DEADLINE_RESCHEDULE));
    }

    @Test
    void weakPriorityChangeBlocksHardDeadlineTask() {
        AgentReview review = review("LOW", false);
        AgentTask source = hardDeadlineTask(109L, LocalDate.of(2026, 7, 19), "LOW");
        AgentReviewPlanSuggestion suggestion = prioritySuggestion(
                309L, source.getId(), "MEDIUM", "LOW", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(source), LocalDate.of(2026, 7, 19));

        assertTrue(result.items().isEmpty());
        assertTrue(result.blockers().stream().anyMatch(message -> message.contains("时限任务的优先级")));
    }

    @Test
    void normalPriorityChangeWarnsForHardDeadlineTask() {
        AgentReview review = review("HIGH", false);
        AgentTask source = hardDeadlineTask(110L, LocalDate.of(2026, 7, 19), "LOW");
        AgentReviewPlanSuggestion suggestion = prioritySuggestion(
                310L, source.getId(), "MEDIUM", "HIGH", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(source), LocalDate.of(2026, 7, 19));

        assertEquals(1, result.items().size());
        assertTrue(result.items().get(0).warningCodes()
                .contains(AgentPlanPreviewValidator.WARNING_DEADLINE_PRIORITY_CHANGE));
    }

    @Test
    void weakCarryOverBlocksHardDeadlineTask() {
        AgentReview review = review("LOW", false);
        AgentTask source = hardDeadlineTask(112L, LocalDate.of(2026, 7, 18), "MEDIUM");
        AgentReviewPlanSuggestion suggestion = suggestion(312L, "CARRY_OVER", source.getId(), "LOW", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(), LocalDate.of(2026, 7, 19));

        assertTrue(result.items().isEmpty());
        assertTrue(result.blockers().stream().anyMatch(message -> message.contains("不允许承接有明确面试")));
    }

    @Test
    void normalCarryOverWarnsForHardDeadlineTask() {
        AgentReview review = review("HIGH", false);
        AgentTask source = hardDeadlineTask(113L, LocalDate.of(2026, 7, 18), "MEDIUM");
        AgentReviewPlanSuggestion suggestion = suggestion(313L, "CARRY_OVER", source.getId(), "HIGH", false);

        AgentPlanPreviewPlanner.PlanningResult result = plan(
                review, suggestion, source, List.of(), LocalDate.of(2026, 7, 19));

        assertEquals(1, result.items().size());
        assertTrue(result.items().get(0).warningCodes()
                .contains(AgentPlanPreviewValidator.WARNING_DEADLINE_RESCHEDULE));
    }

    private AgentPlanPreviewPlanner.PlanningResult plan(AgentReview review,
                                                        AgentReviewPlanSuggestion suggestion,
                                                        AgentTask source,
                                                        List<AgentTask> targetTasks,
                                                        LocalDate targetDate) {
        return planner.plan(new AgentPlanPreviewPlanner.PlanningInput(
                review,
                List.of(suggestion),
                List.of(source),
                List.of(source),
                targetTasks,
                null,
                null,
                List.of(),
                targetDate,
                120,
                LocalDate.of(2026, 7, 18)));
    }

    private AgentReview review(String confidence, boolean fallback) {
        AgentReview review = new AgentReview();
        review.setId(88L);
        review.setUserId(10L);
        review.setTargetJobId(11L);
        review.setTargetScopeKey("JOB:11");
        review.setReviewVersion(2);
        review.setConfidenceLevel(confidence);
        review.setFallback(fallback);
        return review;
    }

    private AgentTask task(Long id, LocalDate dueDate, String priority, Integer minutes) {
        AgentTask task = new AgentTask();
        task.setId(id);
        task.setUserId(10L);
        task.setTargetJobId(11L);
        task.setTaskType("QUESTION_PRACTICE");
        task.setTitle("复盘 Java 高频题");
        task.setPriority(priority);
        task.setEstimatedMinutes(minutes);
        task.setRelatedSkillCode("JAVA");
        task.setStatus("TODO");
        task.setDueDate(dueDate);
        task.setDeleted(0);
        return task;
    }

    private AgentTask hardDeadlineTask(Long id, LocalDate dueDate, String priority) {
        AgentTask task = task(id, dueDate, priority, 30);
        task.setTaskType("INTERVIEW");
        task.setRelatedBizType("INTERVIEW");
        task.setRelatedBizId(900L + id);
        return task;
    }

    private AgentReviewPlanSuggestion prioritySuggestion(Long id,
                                                         Long taskId,
                                                         String targetPriority,
                                                         String confidence,
                                                         boolean fallback) {
        AgentPlanSuggestionIntentDTO intent = new AgentPlanSuggestionIntentDTO();
        intent.setSourceTaskId(taskId);
        intent.setRelatedTaskRefs(List.of("task:" + taskId));
        intent.setTargetPriority(targetPriority);
        return suggestion(id, "CHANGE_PRIORITY", intent, confidence, fallback);
    }

    private AgentReviewPlanSuggestion suggestion(Long id,
                                                 String intentType,
                                                 Long taskId,
                                                 String confidence,
                                                 boolean fallback) {
        AgentPlanSuggestionIntentDTO intent = new AgentPlanSuggestionIntentDTO();
        intent.setSourceTaskId(taskId);
        intent.setRelatedTaskRefs(List.of("task:" + taskId));
        return suggestion(id, intentType, intent, confidence, fallback);
    }

    private AgentReviewPlanSuggestion suggestion(Long id,
                                                 String intentType,
                                                 AgentPlanSuggestionIntentDTO intent,
                                                 String confidence,
                                                 boolean fallback) {
        AgentReviewPlanSuggestion suggestion = new AgentReviewPlanSuggestion();
        suggestion.setId(id);
        suggestion.setUserId(10L);
        suggestion.setReviewId(88L);
        suggestion.setReviewVersion(2);
        suggestion.setSuggestionFingerprint("fingerprint-" + id);
        suggestion.setTitle("调整任务");
        suggestion.setIntentType(intentType);
        suggestion.setIntentJson(jsonCodec.write(intent));
        suggestion.setDecisionStatus("ACCEPTED");
        suggestion.setDecisionVersion(1);
        suggestion.setConfidenceLevel(confidence);
        suggestion.setFallback(fallback);
        return suggestion;
    }
}
