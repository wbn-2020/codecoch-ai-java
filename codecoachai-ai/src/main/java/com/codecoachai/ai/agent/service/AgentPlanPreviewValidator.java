package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AgentPlanTaskSnapshotDTO;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgentPlanPreviewValidator {

    public static final String WARNING_LOW_CONFIDENCE = "LOW_CONFIDENCE_REVIEW";
    public static final String WARNING_ALL_SCOPE = "ALL_JOB_SCOPE";
    public static final String WARNING_NET_TIME_OVER_60 = "NET_TIME_OVER_60";
    public static final String WARNING_HIGH_PRIORITY_REMOVAL = "HIGH_PRIORITY_REMOVAL";
    public static final String WARNING_DEADLINE_RESCHEDULE = "DEADLINE_RESCHEDULE";
    public static final String WARNING_DEADLINE_PRIORITY_CHANGE = "DEADLINE_PRIORITY_CHANGE";
    public static final String WARNING_WEAK_SINGLE_CHANGE = "LOW_CONFIDENCE_SINGLE_CHANGE";

    private static final int MAX_TASK_COUNT = 5;
    private static final int MAX_TOTAL_MINUTES = 480;

    public ValidationResult validate(LocalDate currentDate,
                                     LocalDate targetDate,
                                     Integer maxTotalMinutes,
                                     List<AgentPlanTaskSnapshotDTO> projectedTasks,
                                     int beforeMinutes,
                                     boolean allScope,
                                     boolean weakAdjustment) {
        List<String> blockers = new ArrayList<>();
        Set<String> warnings = new LinkedHashSet<>();
        LocalDate today = currentDate == null ? LocalDate.now() : currentDate;
        if (targetDate == null) {
            blockers.add("目标日期不能为空。");
        } else {
            if (targetDate.isBefore(today)) {
                blockers.add("目标日期不能早于当前日期。");
            }
            LocalDate maxDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusDays(7);
            if (targetDate.isAfter(maxDate)) {
                blockers.add("目标日期超出允许调整范围，请选择当前周或下一周内的日期。");
            }
        }

        List<AgentPlanTaskSnapshotDTO> tasks = projectedTasks == null ? List.of() : projectedTasks;
        if (tasks.size() > MAX_TASK_COUNT) {
            blockers.add("调整后有效任务不能超过 5 项。");
        }
        int afterMinutes = tasks.stream().mapToInt(this::minutes).sum();
        int budget = maxTotalMinutes == null ? 120 : Math.min(maxTotalMinutes, MAX_TOTAL_MINUTES);
        if (afterMinutes > budget) {
            blockers.add("调整后总时长超过当前时间预算。");
        }
        if (afterMinutes > MAX_TOTAL_MINUTES) {
            blockers.add("调整后总时长不能超过 480 分钟。");
        }
        if (tasks.stream().anyMatch(task -> minutes(task) < 5 || minutes(task) > 180)) {
            blockers.add("任务预计时长必须在 5 到 180 分钟之间。");
        }
        if (afterMinutes - beforeMinutes > 60) {
            warnings.add(WARNING_NET_TIME_OVER_60);
        }
        if (allScope) {
            warnings.add(WARNING_ALL_SCOPE);
        }
        if (weakAdjustment) {
            warnings.add(WARNING_LOW_CONFIDENCE);
        }
        return new ValidationResult(List.copyOf(warnings), List.copyOf(blockers));
    }

    public boolean isOpenStatus(String status) {
        return "TODO".equals(status) || "DOING".equals(status) || "DEFERRED".equals(status);
    }

    public boolean hasHardBusinessDeadline(AgentPlanTaskSnapshotDTO task) {
        if (task == null) {
            return false;
        }
        String taskType = task.getTaskType();
        String bizType = task.getRelatedBizType();
        return "INTERVIEW".equals(taskType)
                || "APPLICATION_FOLLOW_UP".equals(taskType)
                || "INTERVIEW".equals(bizType)
                || "JOB_APPLICATION".equals(bizType)
                || "JOB_APPLICATION_EVENT".equals(bizType);
    }

    private int minutes(AgentPlanTaskSnapshotDTO task) {
        return task == null || task.getEstimatedMinutes() == null ? 0 : task.getEstimatedMinutes();
    }

    public record ValidationResult(List<String> warnings, List<String> blockers) {
    }
}
