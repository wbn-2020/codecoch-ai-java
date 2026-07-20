package com.codecoachai.ai.agent.service.support;

import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanSuggestion;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlanItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AgentAdaptivePlanHashUtils {

    private AgentAdaptivePlanHashUtils() {
    }

    public static String reviewSourceSnapshotHash(Long userId,
                                                  Long targetJobId,
                                                  LocalDate date,
                                                  List<AgentTask> tasks,
                                                  List<AgentRun> runs) {
        List<String> values = new ArrayList<>();
        values.add("version=review-source-v2");
        values.add("user=" + Objects.toString(userId, ""));
        values.add("scope=" + targetScopeKey(targetJobId));
        values.add("date=" + Objects.toString(date, ""));
        safeTasks(tasks).stream()
                .sorted(Comparator.comparing(AgentTask::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(task -> values.add("task=" + String.join("|",
                        Objects.toString(task.getId(), ""),
                        Objects.toString(task.getStatus(), ""),
                        Objects.toString(task.getDueDate(), ""),
                        Objects.toString(task.getTaskType(), ""),
                        Objects.toString(task.getPriority(), ""),
                        Objects.toString(task.getEstimatedMinutes(), ""),
                        normalizeText(task.getRelatedSkillCode()),
                        normalizeText(task.getRelatedSkillName()),
                        Objects.toString(task.getRelatedBizType(), ""),
                        Objects.toString(task.getRelatedBizId(), ""),
                        sha256(normalizeText(task.getTitle())),
                        sha256(normalizeText(task.getDescription())),
                        sha256(normalizeText(task.getReason())),
                        sha256(normalizeText(task.getActionUrl())))));
        safeRuns(runs).stream()
                .sorted(Comparator.comparing(AgentRun::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(run -> values.add("run=" + String.join("|",
                        Objects.toString(run.getId(), ""),
                        Objects.toString(run.getTargetJobId(), ""),
                        Objects.toString(run.getPlanDate(), ""),
                        Objects.toString(run.getAgentType(), ""),
                        Objects.toString(run.getStatus(), ""),
                        Objects.toString(run.getDeleted(), ""))));
        return sha256(String.join("\n", values));
    }

    public static String reviewSourceSnapshotHash(Long targetJobId, LocalDate date, List<AgentTask> tasks) {
        return reviewSourceSnapshotHash(null, targetJobId, date, tasks, List.of());
    }

    public static String taskBaselineHash(List<AgentTask> tasks) {
        return sha256(safeTasks(tasks).stream()
                .sorted(Comparator.comparing(AgentTask::getId, Comparator.nullsLast(Long::compareTo)))
                .map(task -> String.join("|",
                        Objects.toString(task.getId(), ""),
                        Objects.toString(task.getStatus(), ""),
                        Objects.toString(task.getDueDate(), ""),
                        Objects.toString(task.getPriority(), ""),
                        Objects.toString(task.getEstimatedMinutes(), ""),
                        Objects.toString(task.getTaskType(), ""),
                        Objects.toString(task.getRelatedBizType(), ""),
                        Objects.toString(task.getRelatedBizId(), ""),
                        Objects.toString(task.getPlanChangeItemId(), ""),
                        Objects.toString(task.getUpdatedAt(), ""),
                        Objects.toString(task.getDeleted(), "")))
                .collect(Collectors.joining("\n")));
    }

    public static String weekItemBaselineHash(List<AgentWeekPlanItem> items) {
        List<AgentWeekPlanItem> safeItems = items == null ? List.of() : items;
        return sha256(safeItems.stream()
                .sorted(Comparator.comparing(AgentWeekPlanItem::getId, Comparator.nullsLast(Long::compareTo)))
                .map(item -> String.join("|",
                        Objects.toString(item.getId(), ""),
                        Objects.toString(item.getAgentTaskId(), ""),
                        Objects.toString(item.getLayer(), ""),
                        Objects.toString(item.getItemStatus(), ""),
                        Objects.toString(item.getPlannedDate(), ""),
                        Objects.toString(item.getPriority(), ""),
                        Objects.toString(item.getSnapshotVersion(), ""),
                        Objects.toString(item.getUpdatedAt(), ""),
                        Objects.toString(item.getDeleted(), "")))
                .collect(Collectors.joining("\n")));
    }

    public static String selectionHash(List<AgentReviewPlanSuggestion> suggestions) {
        List<AgentReviewPlanSuggestion> safeSuggestions = suggestions == null ? List.of() : suggestions;
        return sha256(safeSuggestions.stream()
                .sorted(Comparator.comparing(AgentReviewPlanSuggestion::getId, Comparator.nullsLast(Long::compareTo)))
                .map(item -> String.join("|",
                        Objects.toString(item.getId(), ""),
                        Objects.toString(item.getReviewVersion(), ""),
                        Objects.toString(item.getDecisionVersion(), ""),
                        Objects.toString(item.getDecisionStatus(), ""),
                        Objects.toString(item.getSuggestionFingerprint(), "")))
                .collect(Collectors.joining("\n")));
    }

    public static String targetScopeKey(Long targetJobId) {
        return targetJobId == null ? "ALL" : "JOB:" + targetJobId;
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算自适应计划安全哈希", ex);
        }
    }

    private static List<AgentTask> safeTasks(List<AgentTask> tasks) {
        return tasks == null ? List.of() : tasks;
    }

    private static List<AgentRun> safeRuns(List<AgentRun> runs) {
        return runs == null ? List.of() : runs;
    }
}
