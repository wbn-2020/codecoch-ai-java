package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult.PlanTask;
import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.domain.enums.AgentTaskPriorityEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskTypeEnum;
import com.codecoachai.ai.agent.service.AgentOutputValidator;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentOutputValidatorImpl implements AgentOutputValidator {

    @Override
    public void validateDailyPlan(DailyPlanResult result, List<CandidateTask> candidates, int taskCount, int maxTotalMinutes) {
        if (result == null || !StringUtils.hasText(result.getSummary())) {
            invalid("summary", "REQUIRED");
        }
        validateUserText(result.getSummary(), "summary");
        List<PlanTask> tasks = result.getTasks();
        if (tasks == null || tasks.isEmpty() || tasks.size() > 5 || tasks.size() > taskCount) {
            invalid("tasks", "TASK_COUNT");
        }
        Map<String, CandidateTask> candidateById = new HashMap<>();
        if (candidates != null) {
            for (CandidateTask candidate : candidates) {
                if (candidate != null && StringUtils.hasText(candidate.getCandidateId())) {
                    if (candidateById.putIfAbsent(candidate.getCandidateId(), candidate) != null) {
                        invalid("candidates", "DUPLICATE_CANDIDATE_ID");
                    }
                }
            }
        }
        Set<String> seen = new HashSet<>();
        Set<String> seenCandidates = new HashSet<>();
        int totalMinutes = 0;
        for (int i = 0; i < tasks.size(); i++) {
            PlanTask task = tasks.get(i);
            validateTask(task, candidateById, seen, seenCandidates, "tasks[" + i + "]");
            totalMinutes += task.getEstimatedMinutes();
        }
        if (totalMinutes > maxTotalMinutes) {
            invalid("tasks", "TOTAL_MINUTES");
        }
    }

    private void validateTask(PlanTask task, Map<String, CandidateTask> candidates, Set<String> seen,
                              Set<String> seenCandidates, String path) {
        if (task == null || !StringUtils.hasText(task.getTitle()) || !StringUtils.hasText(task.getReason())) {
            invalid(path, "REQUIRED");
        }
        validateUserText(task.getTitle(), path + ".title");
        validateUserText(task.getDescription(), path + ".description");
        validateUserText(task.getReason(), path + ".reason");
        if (task.getEstimatedMinutes() == null || task.getEstimatedMinutes() < 5 || task.getEstimatedMinutes() > 180) {
            invalid(path + ".estimatedMinutes", "TASK_MINUTES");
        }
        task.setType(enumValue(AgentTaskTypeEnum.class, task.getType(), path + ".type"));
        task.setPriority(enumValue(AgentTaskPriorityEnum.class, task.getPriority(), path + ".priority"));
        String candidateId = task.getCandidateId() == null ? null : task.getCandidateId().trim();
        CandidateTask candidate = candidates.get(candidateId);
        if (candidate == null) {
            invalid(path + ".candidateId", "UNKNOWN_CANDIDATE");
        }
        task.setCandidateId(candidate.getCandidateId());
        if (!task.getType().equals(candidate.getType())) {
            invalid(path + ".type", "CANDIDATE_TYPE_MISMATCH");
        }
        // Bind both the persisted snapshot and task row to server-owned candidate metadata, including nulls.
        task.setRelatedSkillCode(candidate.getRelatedSkillCode());
        task.setRelatedSkillName(candidate.getRelatedSkillName());
        task.setRelatedBizType(candidate.getRelatedBizType());
        task.setRelatedBizId(candidate.getRelatedBizId());
        task.setActionUrl(candidate.getActionUrl());
        if (!seenCandidates.add(task.getCandidateId())) {
            invalid(path + ".candidateId", "DUPLICATE_CANDIDATE");
        }
        String duplicateKey = task.getType() + "::" + task.getTitle().trim();
        if (!seen.add(duplicateKey)) {
            invalid(path + ".title", "DUPLICATE_TASK");
        }
    }

    private void validateUserText(String value, String path) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String text = value.trim();
        if (!containsChinese(text)) {
            invalid(path, "CHINESE_REQUIRED");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("fallback")
                || lower.contains("aicalllogid")
                || lower.contains("candidate task")
                || lower.contains("candidateid")
                || text.contains("AGENT_")
                || text.contains("调用日志")) {
            invalid(path, "INTERNAL_TEXT");
        }
        if (text.startsWith("Practice ")
                || text.startsWith("Improve resume evidence")
                || text.startsWith("Run a target-job")
                || text.startsWith("Review core")) {
            invalid(path, "LEGACY_ENGLISH_TEXT");
        }
    }

    private boolean containsChinese(String value) {
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(i));
            if (Character.UnicodeScript.HAN.equals(script)) {
                return true;
            }
        }
        return false;
    }

    private <E extends Enum<E>> String enumValue(Class<E> enumClass, String value, String path) {
        if (!StringUtils.hasText(value)) {
            invalid(path, "ENUM_REQUIRED");
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            invalid(path, "ENUM_INVALID");
            return null;
        }
    }

    private void invalid(String path, String rule) {
        // Record only structural coordinates and rule codes, never model text or candidate identifiers.
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, AgentErrorCode.OUTPUT_VALIDATE_FAILED,
                false, null, Map.of(path, rule));
    }
}
