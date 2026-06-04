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
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentOutputValidatorImpl implements AgentOutputValidator {

    @Override
    public void validateDailyPlan(DailyPlanResult result, List<CandidateTask> candidates, int taskCount, int maxTotalMinutes) {
        if (result == null || !StringUtils.hasText(result.getSummary())) {
            invalid();
        }
        validateUserText(result.getSummary());
        List<PlanTask> tasks = result.getTasks();
        if (tasks == null || tasks.isEmpty() || tasks.size() > 5 || tasks.size() > taskCount) {
            invalid();
        }
        Set<String> candidateIds = new HashSet<>();
        for (CandidateTask candidate : candidates) {
            candidateIds.add(candidate.getCandidateId());
        }
        Set<String> seen = new HashSet<>();
        int totalMinutes = 0;
        for (PlanTask task : tasks) {
            validateTask(task, candidateIds, seen);
            totalMinutes += task.getEstimatedMinutes();
        }
        if (totalMinutes > maxTotalMinutes) {
            invalid();
        }
    }

    private void validateTask(PlanTask task, Set<String> candidateIds, Set<String> seen) {
        if (task == null || !StringUtils.hasText(task.getTitle()) || !StringUtils.hasText(task.getReason())) {
            invalid();
        }
        validateUserText(task.getTitle());
        validateUserText(task.getDescription());
        validateUserText(task.getReason());
        if (task.getEstimatedMinutes() == null || task.getEstimatedMinutes() < 5 || task.getEstimatedMinutes() > 180) {
            invalid();
        }
        enumValue(AgentTaskTypeEnum.class, task.getType());
        enumValue(AgentTaskPriorityEnum.class, task.getPriority());
        if (!StringUtils.hasText(task.getCandidateId()) || !candidateIds.contains(task.getCandidateId())) {
            invalid();
        }
        String duplicateKey = task.getType() + "::" + task.getTitle();
        if (!seen.add(duplicateKey)) {
            invalid();
        }
    }

    private void validateUserText(String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String text = value.trim();
        if (!containsChinese(text)) {
            invalid();
        }
        String lower = text.toLowerCase();
        if (lower.contains("fallback")
                || lower.contains("aicalllogid")
                || lower.contains("deepseek")
                || lower.contains("rest api")
                || lower.contains("candidate task")
                || lower.contains("candidateid")
                || text.contains("AGENT_")
                || text.contains("DTO")
                || text.contains("后端接口")
                || text.contains("调用日志")) {
            invalid();
        }
        if (text.contains("Java Backend")
                || text.startsWith("Practice ")
                || text.startsWith("Improve resume evidence")
                || text.startsWith("Run a target-job")
                || text.startsWith("Review core")) {
            invalid();
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

    private <E extends Enum<E>> void enumValue(Class<E> enumClass, String value) {
        if (!StringUtils.hasText(value)) {
            invalid();
        }
        try {
            Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ex) {
            invalid();
        }
    }

    private void invalid() {
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, AgentErrorCode.OUTPUT_VALIDATE_FAILED);
    }
}
