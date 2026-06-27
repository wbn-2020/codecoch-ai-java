package com.codecoachai.ai.agent.convert;

import com.codecoachai.ai.agent.domain.context.DailyPlanResult.FocusSkill;
import com.codecoachai.ai.agent.domain.enums.AgentTaskTypeEnum;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.domain.vo.SkillTagVO;
import com.codecoachai.ai.domain.enums.AiResultSourceEnum;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public final class AgentConvert {

    private static final String RAW_ACCESS_PERMISSION = "admin:ai:log:raw:view";

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
        vo.setEstimatedEffortMinutes(task.getEstimatedMinutes());
        vo.setRelatedSkillCode(task.getRelatedSkillCode());
        vo.setRelatedSkillName(task.getRelatedSkillName());
        vo.setRelatedBizType(task.getRelatedBizType());
        vo.setRelatedBizId(task.getRelatedBizId());
        vo.setActionUrl(task.getActionUrl());
        vo.setActionType(taskActionType(task));
        applyTaskTrustEvidence(vo, task);
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

    private static void applyTaskTrustEvidence(AgentTaskVO vo, AgentTask task) {
        String sourceType = firstText(task.getRelatedBizType(), task.getTaskType(), "JOB_COACH_AGENT_TASK");
        Long sourceId = task.getRelatedBizId() != null ? task.getRelatedBizId() : task.getId();
        boolean hasRun = task.getAgentRunId() != null;
        boolean hasReason = StringUtils.hasText(task.getReason());
        boolean hasBusinessEvidence = StringUtils.hasText(task.getRelatedBizType()) || task.getRelatedBizId() != null;
        boolean hasAction = StringUtils.hasText(task.getActionUrl());
        boolean degradedStatus = "SKIPPED".equalsIgnoreCase(task.getStatus()) || "EXPIRED".equalsIgnoreCase(task.getStatus());

        String trustStatus;
        if (degradedStatus || (!hasRun && !hasReason && !hasBusinessEvidence && !hasAction)) {
            trustStatus = "FALLBACK";
        } else if (hasRun && hasReason && (hasBusinessEvidence || hasAction)) {
            trustStatus = "VERIFIED";
        } else {
            trustStatus = "PARTIAL";
        }

        vo.setSourceType(sourceType);
        vo.setSourceId(sourceId);
        vo.setTrustStatus(trustStatus);
        vo.setFallback("FALLBACK".equals(trustStatus));
        vo.setEvidenceSummary(taskEvidenceSummary(task, hasRun, hasReason, hasBusinessEvidence, hasAction, degradedStatus));
    }

    private static String taskEvidenceSummary(AgentTask task, boolean hasRun, boolean hasReason,
                                              boolean hasBusinessEvidence, boolean hasAction, boolean degradedStatus) {
        List<String> parts = new ArrayList<>();
        if (hasRun) {
            parts.add("计划生成详情可查看");
        }
        if (hasBusinessEvidence) {
            String sourceLabel = businessSourceLabel(task.getRelatedBizType());
            parts.add(task.getRelatedBizId() == null ? sourceLabel : sourceLabel + "已绑定");
        }
        if (StringUtils.hasText(task.getRelatedSkillName())) {
            parts.add("聚焦技能：" + task.getRelatedSkillName());
        }
        if (hasReason) {
            parts.add("推荐理由已返回");
        }
        if (hasAction) {
            parts.add("行动入口已记录");
        }
        if (degradedStatus) {
            parts.add("任务已跳过或过期");
        }
        if (parts.isEmpty()) {
            return "基于现有资料生成入门任务，并已标注资料缺口";
        }
        return String.join("；", parts);
    }

    private static String businessSourceLabel(String sourceType) {
        String type = StringUtils.hasText(sourceType) ? sourceType.trim().toUpperCase() : "";
        return switch (type) {
            case "TARGET_JOB" -> "来自目标岗位/JD";
            case "RESUME_JOB_MATCH", "RESUME_MATCH" -> "来自匹配报告";
            case "QUESTION_RECOMMENDATION" -> "来自推荐题";
            case "QUESTION_PRACTICE" -> "来自题库练习";
            case "WRONG_QUESTION_REVIEW" -> "来自错题复习";
            case "INTERVIEW" -> "来自模拟面试";
            case "INTERVIEW_REPORT" -> "来自面试报告";
            case "RESUME_OPTIMIZE" -> "来自简历证据";
            case "TRAINING_MATERIAL" -> "来自训练素材";
            default -> "来自智能教练";
        };
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static String taskActionType(AgentTask task) {
        String type = firstText(task.getTaskType(), task.getRelatedBizType(), "");
        if (!StringUtils.hasText(type)) {
            return "OPEN";
        }
        try {
            AgentTaskTypeEnum taskType = AgentTaskTypeEnum.valueOf(type.trim().toUpperCase());
            return switch (taskType) {
                case QUESTION_PRACTICE, WRONG_QUESTION_REVIEW, KNOWLEDGE_REVIEW, SKILL_REVIEW -> "PRACTICE";
                case INTERVIEW, REPORT_REVIEW -> "INTERVIEW";
                case RESUME_OPTIMIZE -> "RESUME";
                case APPLICATION_FOLLOW_UP -> "FOLLOW_UP";
                case STUDY_TASK -> "LEARN";
            };
        } catch (IllegalArgumentException ex) {
            return StringUtils.hasText(task.getActionUrl()) ? "OPEN" : type.trim().toUpperCase();
        }
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
        vo.setRawAvailable(true);
        vo.setRawAccessPermission(RAW_ACCESS_PERMISSION);
        vo.setPromptType(run.getPromptType());
        vo.setPromptVersionId(run.getPromptVersionId());
        vo.setModelName(run.getModelName());
        vo.setTraceId(run.getTraceId());
        vo.setAiCallLogId(run.getAiCallLogId());
        vo.setResultSource(run.getResultSource());
        vo.setResultSourceLabel(aiResultSourceLabel(run.getResultSource()));
        vo.setFallback(isFallbackAiResultSource(run.getResultSource()));
        vo.setMock(isMockAiResultSource(run.getResultSource()));
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

    public static String aiResultSourceLabel(String source) {
        AiResultSourceEnum resultSource = aiResultSourceEnum(source);
        return resultSource == null ? null : resultSource.getLabel();
    }

    public static boolean isFallbackAiResultSource(String source) {
        return AiResultSourceEnum.FALLBACK.name().equals(source);
    }

    public static boolean isMockAiResultSource(String source) {
        return AiResultSourceEnum.MOCK.name().equals(source);
    }

    private static AiResultSourceEnum aiResultSourceEnum(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        try {
            return AiResultSourceEnum.valueOf(source.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
