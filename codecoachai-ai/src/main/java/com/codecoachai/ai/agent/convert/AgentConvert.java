package com.codecoachai.ai.agent.convert;

import com.codecoachai.ai.agent.domain.context.DailyPlanResult.FocusSkill;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.domain.vo.SkillTagVO;

public final class AgentConvert {

    private AgentConvert() {
    }

    public static AgentTaskVO toTaskVO(AgentTask task) {
        AgentTaskVO vo = new AgentTaskVO();
        vo.setId(task.getId());
        vo.setRunId(task.getAgentRunId());
        vo.setTargetJobId(task.getTargetJobId());
        vo.setCandidateId(task.getCandidateId());
        vo.setTaskType(task.getTaskType());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setReason(task.getReason());
        vo.setPriority(task.getPriority());
        vo.setEstimatedMinutes(task.getEstimatedMinutes());
        vo.setRelatedSkillCode(task.getRelatedSkillCode());
        vo.setRelatedSkillName(task.getRelatedSkillName());
        vo.setRelatedBizType(task.getRelatedBizType());
        vo.setRelatedBizId(task.getRelatedBizId());
        vo.setActionUrl(task.getActionUrl());
        vo.setStatus(task.getStatus());
        vo.setSkipReason(task.getSkipReason());
        vo.setDueDate(task.getDueDate());
        vo.setStartedAt(task.getStartedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setSkippedAt(task.getSkippedAt());
        vo.setSortOrder(task.getSortOrder());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    public static AgentRunDetailVO toRunDetailVO(AgentRun run) {
        AgentRunDetailVO vo = new AgentRunDetailVO();
        vo.setId(run.getId());
        vo.setUserId(run.getUserId());
        vo.setAgentType(run.getAgentType());
        vo.setTargetJobId(run.getTargetJobId());
        vo.setPlanDate(run.getPlanDate());
        vo.setTriggerType(run.getTriggerType());
        vo.setStatus(run.getStatus());
        vo.setInputSnapshotJson(run.getInputSnapshotJson());
        vo.setOutputJson(run.getOutputJson());
        vo.setRawOutputText(run.getRawOutputText());
        vo.setPromptType(run.getPromptType());
        vo.setPromptVersionId(run.getPromptVersionId());
        vo.setModelName(run.getModelName());
        vo.setTraceId(run.getTraceId());
        vo.setAiCallLogId(run.getAiCallLogId());
        vo.setTokenInput(run.getTokenInput());
        vo.setTokenOutput(run.getTokenOutput());
        vo.setDurationMs(run.getDurationMs());
        vo.setErrorCode(run.getErrorCode());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setCreatedAt(run.getCreatedAt());
        return vo;
    }

    public static SkillTagVO toSkillTagVO(FocusSkill skill) {
        SkillTagVO vo = new SkillTagVO();
        vo.setCode(skill.getCode());
        vo.setName(skill.getName());
        return vo;
    }
}
