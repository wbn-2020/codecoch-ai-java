package com.codecoachai.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.interview.domain.entity.StudyTask;
import com.codecoachai.interview.mapper.StudyTaskMapper;
import com.codecoachai.question.domain.entity.UserQuestionRecord;
import com.codecoachai.question.mapper.UserQuestionRecordMapper;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习计划动态调整 Controller。
 * 根据用户表现自动调整计划：
 * - 未完成任务自动延期
 * - 连续完成的知识点降低频率
 * - 连续错误的知识点增加复习任务
 */
@Slf4j
@Tag(name = "学习计划动态调整")
@RestController
@RequestMapping("/study-plans")
@RequiredArgsConstructor
public class StudyPlanAdjustController {

    private final StudyTaskMapper studyTaskMapper;
    private final UserQuestionRecordMapper userQuestionRecordMapper;

    @Operation(summary = "触发计划动态调整（将过期未完成任务延期到今天之后）")
    @PostMapping("/{planId}/adjust")
    public Result<AdjustResultVO> adjust(@PathVariable Long planId) {
        Long userId = SecurityAssert.requireLoginUserId();
        LocalDate today = LocalDate.now();

        // 查询该计划下所有过期未完成的任务
        List<StudyTask> overdueTasks = studyTaskMapper.selectList(
                new LambdaQueryWrapper<StudyTask>()
                        .eq(StudyTask::getPlanId, planId)
                        .eq(StudyTask::getUserId, userId)
                        .in(StudyTask::getTaskStatus, List.of("TODO", "PENDING"))
                        .lt(StudyTask::getPlannedDate, today));

        if (overdueTasks.isEmpty()) {
            int reviewAdded = addDueWrongQuestionReviews(planId, userId, today);
            AdjustResultVO vo = new AdjustResultVO();
            vo.setRescheduledCount(0);
            vo.setAddedReviewCount(reviewAdded);
            vo.setMessage(reviewAdded > 0
                    ? "任务均按时完成；已把 " + reviewAdded + " 个到期错题加入复习"
                    : "无需调整，所有任务按时完成");
            return Result.success(vo);
        }

        // 将过期任务重新安排到今天及之后
        int rescheduled = 0;
        LocalDate nextDate = today;
        // 查询今天及之后已有多少任务
        Long todayTaskCount = studyTaskMapper.selectCount(
                new LambdaQueryWrapper<StudyTask>()
                        .eq(StudyTask::getPlanId, planId)
                        .eq(StudyTask::getUserId, userId)
                        .eq(StudyTask::getPlannedDate, today)
                        .ne(StudyTask::getTaskStatus, "SKIPPED"));

        // 每天最多安排 5 个任务
        int dailyLimit = 5;
        int todaySlots = (int) (dailyLimit - todayTaskCount);
        if (todaySlots < 0) todaySlots = 0;

        for (StudyTask task : overdueTasks) {
            if (todaySlots <= 0) {
                nextDate = nextDate.plusDays(1);
                todaySlots = dailyLimit;
            }
            studyTaskMapper.update(null,
                    new LambdaUpdateWrapper<StudyTask>()
                            .eq(StudyTask::getId, task.getId())
                            .set(StudyTask::getPlannedDate, nextDate));
            todaySlots--;
            rescheduled++;
        }

        int reviewAdded = addDueWrongQuestionReviews(planId, userId, today);

        AdjustResultVO vo = new AdjustResultVO();
        vo.setRescheduledCount(rescheduled);
        vo.setAddedReviewCount(reviewAdded);
        vo.setMessage("已将 " + rescheduled + " 个过期任务重新安排"
                + (reviewAdded > 0 ? "，并把 " + reviewAdded + " 个到期错题加入复习" : ""));
        return Result.success(vo);
    }

    /**
     * 与 V4_140 错题间隔复习联动：把今天已到期的错题（next_review_at <= 现在）
     * 生成"错题复习"任务排入计划（每天最多 3 个，避免挤占正常任务）。
     * 幂等：同一天同题已存在复习任务则跳过（按任务标题识别）。
     */
    private int addDueWrongQuestionReviews(Long planId, Long userId, LocalDate today) {
        List<UserQuestionRecord> dueRecords = userQuestionRecordMapper.selectList(
                new LambdaQueryWrapper<UserQuestionRecord>()
                        .eq(UserQuestionRecord::getUserId, userId)
                        .eq(UserQuestionRecord::getWrong, 1)
                        .isNotNull(UserQuestionRecord::getNextReviewAt)
                        .le(UserQuestionRecord::getNextReviewAt, LocalDateTime.now())
                        .orderByAsc(UserQuestionRecord::getNextReviewAt)
                        .last("limit 3"));
        if (dueRecords.isEmpty()) {
            return 0;
        }
        List<StudyTask> todayReviewTasks = studyTaskMapper.selectList(
                new LambdaQueryWrapper<StudyTask>()
                        .eq(StudyTask::getPlanId, planId)
                        .eq(StudyTask::getUserId, userId)
                        .eq(StudyTask::getPlannedDate, today)
                        .like(StudyTask::getTaskTitle, "错题复习"));
        java.util.Set<String> existing = todayReviewTasks.stream()
                .map(StudyTask::getTaskTitle)
                .collect(Collectors.toSet());
        int added = 0;
        int order = todayReviewTasks.size();
        for (UserQuestionRecord record : dueRecords) {
            String title = "错题复习 #" + record.getQuestionId();
            if (existing.contains(title)) {
                continue;
            }
            StudyTask task = new StudyTask();
            task.setPlanId(planId);
            task.setUserId(userId);
            task.setPlannedDate(today);
            task.setStageNo(99);
            task.setStageTitle("错题复习");
            task.setTaskOrder(order + added + 1);
            task.setKnowledgePoint("错题复习");
            task.setTaskTitle(title);
            task.setTaskDescription("到期间隔复习：重答该题并对照 AI 点评补齐薄弱点（"
                    + (record.getReviewIntervalDays() == null ? 1 : record.getReviewIntervalDays()) + " 天档）");
            task.setTaskType("REVIEW");
            task.setPriority("HIGH");
            task.setEstimatedMinutes(10);
            task.setAcceptanceCriteria("重答正确，或能完整说出错因与正确思路");
            task.setTaskStatus("TODO");
            task.setRelatedQuestionIdsJson(String.valueOf(record.getQuestionId()));
            studyTaskMapper.insert(task);
            added++;
        }
        return added;
    }

    @Operation(summary = "查看计划执行统计（用于判断是否需要调整）")
    @GetMapping("/{planId}/adjust/stats")
    public Result<PlanStatsVO> adjustStats(@PathVariable Long planId) {
        Long userId = SecurityAssert.requireLoginUserId();
        LocalDate today = LocalDate.now();

        List<StudyTask> allTasks = studyTaskMapper.selectList(
                new LambdaQueryWrapper<StudyTask>()
                        .eq(StudyTask::getPlanId, planId)
                        .eq(StudyTask::getUserId, userId));

        int total = allTasks.size();
        int completed = 0;
        int skipped = 0;
        int overdue = 0;
        int pending = 0;

        for (StudyTask t : allTasks) {
            switch (t.getTaskStatus()) {
                case "DONE", "COMPLETED" -> completed++;
                case "SKIPPED" -> skipped++;
                default -> {
                    if (t.getPlannedDate() != null && t.getPlannedDate().isBefore(today)) {
                        overdue++;
                    } else {
                        pending++;
                    }
                }
            }
        }

        // 按知识点统计完成率
        Map<String, List<StudyTask>> byKnowledge = allTasks.stream()
                .filter(t -> t.getKnowledgePoint() != null)
                .collect(Collectors.groupingBy(StudyTask::getKnowledgePoint));

        List<KnowledgeStatVO> knowledgeStats = new ArrayList<>();
        for (Map.Entry<String, List<StudyTask>> entry : byKnowledge.entrySet()) {
            List<StudyTask> tasks = entry.getValue();
            long done = tasks.stream().filter(t -> "COMPLETED".equals(t.getTaskStatus())).count();
            KnowledgeStatVO ks = new KnowledgeStatVO();
            ks.setKnowledgePoint(entry.getKey());
            ks.setTotalTasks(tasks.size());
            ks.setCompletedTasks((int) done);
            ks.setCompletionRate(tasks.size() > 0 ? Math.round(done * 100.0 / tasks.size()) : 0);
            knowledgeStats.add(ks);
        }
        knowledgeStats.sort((a, b) -> Long.compare(a.getCompletionRate(), b.getCompletionRate()));

        PlanStatsVO vo = new PlanStatsVO();
        vo.setTotalTasks(total);
        vo.setCompletedTasks(completed);
        vo.setSkippedTasks(skipped);
        vo.setOverdueTasks(overdue);
        vo.setPendingTasks(pending);
        vo.setCompletionRate(total > 0 ? Math.round(completed * 100.0 / total) : 0);
        vo.setNeedAdjust(overdue > 3);
        vo.setKnowledgeStats(knowledgeStats);
        return Result.success(vo);
    }

    @Data
    public static class AdjustResultVO {
        private int rescheduledCount;
        private int addedReviewCount;
        private String message;
    }

    @Data
    public static class PlanStatsVO {
        private int totalTasks;
        private int completedTasks;
        private int skippedTasks;
        private int overdueTasks;
        private int pendingTasks;
        private long completionRate;
        private boolean needAdjust;
        private List<KnowledgeStatVO> knowledgeStats;
    }

    @Data
    public static class KnowledgeStatVO {
        private String knowledgePoint;
        private int totalTasks;
        private int completedTasks;
        private long completionRate;
    }
}
