package com.codecoachai.ai.agent.convert;

import com.codecoachai.ai.agent.domain.context.DailyPlanResult.FocusSkill;
import com.codecoachai.ai.agent.domain.enums.AgentTaskTypeEnum;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.domain.vo.DailyPlanVO;
import com.codecoachai.ai.agent.domain.vo.SkillTagVO;
import com.codecoachai.ai.agent.domain.vo.SuggestionEvidenceSourceVO;
import com.codecoachai.ai.agent.domain.vo.SuggestionQualityGateVO;
import com.codecoachai.ai.domain.enums.AiResultSourceEnum;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public final class AgentConvert {

    private static final String RAW_ACCESS_PERMISSION = "admin:ai:log:raw:view";
    public static final String TRUSTED_RESULT_SCHEMA_VERSION = "V4_TRUSTED_RESULT_V1";

    private AgentConvert() {
    }

    public static AgentTaskVO toTaskVO(AgentTask task) {
        AgentTaskVO vo = new AgentTaskVO();
        vo.setId(task.getId());
        vo.setRunId(task.getAgentRunId());
        vo.setSchemaVersion(TRUSTED_RESULT_SCHEMA_VERSION);
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
        vo.setDeferReason(task.getDeferReason());
        vo.setDueDate(task.getDueDate());
        vo.setStartedAt(task.getStartedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setDeferredAt(task.getDeferredAt());
        vo.setSkippedAt(task.getSkippedAt());
        vo.setSortOrder(task.getSortOrder());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    public static AgentTaskVO applyRunTrace(AgentTaskVO vo, AgentRun run) {
        if (vo == null || run == null) {
            return vo;
        }
        vo.setSchemaVersion(TRUSTED_RESULT_SCHEMA_VERSION);
        vo.setRunId(run.getId());
        vo.setTraceId(run.getTraceId());
        vo.setAiCallLogId(run.getAiCallLogId());
        vo.setPromptVersionId(run.getPromptVersionId());
        vo.setResultSource(run.getResultSource());
        vo.setResultSourceLabel(aiResultSourceLabel(run.getResultSource()));
        vo.setMock(isMockAiResultSource(run.getResultSource()));
        vo.setFallback(Boolean.TRUE.equals(vo.getFallback()) || isFallbackAiResultSource(run.getResultSource()));
        applySuggestionQualityGate(vo);
        return vo;
    }

    public static DailyPlanVO applyRunTrace(DailyPlanVO vo, AgentRun run) {
        if (vo == null || run == null) {
            return vo;
        }
        vo.setSchemaVersion(TRUSTED_RESULT_SCHEMA_VERSION);
        vo.setTraceId(run.getTraceId());
        vo.setAiCallLogId(run.getAiCallLogId());
        vo.setPromptVersionId(run.getPromptVersionId());
        vo.setResultSource(run.getResultSource());
        vo.setResultSourceLabel(aiResultSourceLabel(run.getResultSource()));
        vo.setFallback(isFallbackAiResultSource(run.getResultSource()));
        vo.setMock(isMockAiResultSource(run.getResultSource()));
        vo.setEvidenceSources(planEvidenceSources(run));
        vo.setQualityGate(planQualityGate(vo));
        return vo;
    }

    private static void applyTaskTrustEvidence(AgentTaskVO vo, AgentTask task) {
        String sourceType = firstText(task.getRelatedBizType(), task.getTaskType(), "JOB_COACH_AGENT_TASK");
        Long sourceId = task.getRelatedBizId() != null ? task.getRelatedBizId() : task.getId();
        boolean hasRun = task.getAgentRunId() != null;
        boolean hasReason = StringUtils.hasText(task.getReason());
        boolean hasBusinessEvidence = StringUtils.hasText(task.getRelatedBizType()) || task.getRelatedBizId() != null;
        boolean hasAction = StringUtils.hasText(task.getActionUrl());
        boolean degradedStatus = "DEFERRED".equalsIgnoreCase(task.getStatus())
                || "SKIPPED".equalsIgnoreCase(task.getStatus()) || "EXPIRED".equalsIgnoreCase(task.getStatus());

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
        vo.setEvidenceSources(taskEvidenceSources(vo, task));
        applySuggestionQualityGate(vo);
    }

    private static List<SuggestionEvidenceSourceVO> taskEvidenceSources(AgentTaskVO vo, AgentTask task) {
        SuggestionEvidenceSourceVO source = new SuggestionEvidenceSourceVO();
        source.setId("agent-task:" + task.getId());
        source.setSourceType(vo.getSourceType());
        source.setSourceId(vo.getSourceId());
        source.setSourceTitle(task.getTitle());
        source.setSourceLabel(businessSourceLabel(vo.getSourceType()));
        source.setEvidenceSummary(vo.getEvidenceSummary());
        source.setSourceSummary(vo.getEvidenceSummary());
        source.setTrustStatus(vo.getTrustStatus());
        source.setSourceUpdatedAt(task.getUpdatedAt());
        source.setActionUrl(task.getActionUrl());
        return List.of(source);
    }

    private static void applySuggestionQualityGate(AgentTaskVO vo) {
        SuggestionQualityGateVO gate = new SuggestionQualityGateVO();
        boolean mock = Boolean.TRUE.equals(vo.getMock()) || isMockAiResultSource(vo.getResultSource());
        boolean fallback = Boolean.TRUE.equals(vo.getFallback()) || isFallbackAiResultSource(vo.getResultSource())
                || "FALLBACK".equalsIgnoreCase(vo.getTrustStatus());
        boolean hasTrace = StringUtils.hasText(vo.getTraceId()) || vo.getAiCallLogId() != null || vo.getRunId() != null;
        boolean hasEvidence = vo.getEvidenceSources() != null && !vo.getEvidenceSources().isEmpty();
        if (mock) {
            gate.setGateStatus("WARN");
            gate.setSuggestionStrength("MOCK");
            gate.setReasons(List.of("演示或模拟数据不能作为真实强建议依据"));
        } else if (fallback) {
            gate.setGateStatus("WARN");
            gate.setSuggestionStrength("FALLBACK");
            gate.setReasons(List.of("推荐依据不足或结果为降级输出，需要补充证据后再判断"));
        } else if (!hasTrace) {
            gate.setGateStatus("WARN");
            gate.setSuggestionStrength("WEAK");
            gate.setReasons(List.of("缺少可追踪的 Agent Run、trace 或 AI 调用记录，不能作为强建议"));
        } else if (!hasEvidence || !"VERIFIED".equalsIgnoreCase(vo.getTrustStatus())) {
            gate.setGateStatus("WARN");
            gate.setSuggestionStrength("WEAK");
            gate.setReasons(List.of("证据尚未达到已验证状态，仅作为普通任务建议"));
        } else {
            gate.setGateStatus("PASS");
            gate.setSuggestionStrength("NORMAL");
            gate.setReasons(List.of("任务来源、证据摘要和追踪信息可用于解释建议"));
        }
        vo.setQualityGate(gate);
    }

    private static List<SuggestionEvidenceSourceVO> planEvidenceSources(AgentRun run) {
        SuggestionEvidenceSourceVO source = new SuggestionEvidenceSourceVO();
        source.setId("agent-run:" + run.getId());
        source.setSourceType("AGENT_RUN");
        source.setSourceId(run.getId());
        source.setSourceTitle("Agent 今日计划");
        source.setSourceLabel("Agent 运行");
        source.setEvidenceSummary("计划生成运行记录可追踪");
        source.setSourceSummary("计划生成运行记录可追踪");
        source.setTrustStatus(isFallbackAiResultSource(run.getResultSource()) || isMockAiResultSource(run.getResultSource())
                ? "FALLBACK" : "PARTIAL");
        source.setSourceUpdatedAt(run.getFinishedAt() == null ? run.getUpdatedAt() : run.getFinishedAt());
        return List.of(source);
    }

    private static SuggestionQualityGateVO planQualityGate(DailyPlanVO vo) {
        SuggestionQualityGateVO gate = new SuggestionQualityGateVO();
        if (Boolean.TRUE.equals(vo.getMock())) {
            gate.setGateStatus("WARN");
            gate.setSuggestionStrength("MOCK");
            gate.setReasons(List.of("演示或模拟计划不能作为真实强建议依据"));
        } else if (Boolean.TRUE.equals(vo.getFallback())) {
            gate.setGateStatus("WARN");
            gate.setSuggestionStrength("FALLBACK");
            gate.setReasons(List.of("计划来自降级输出，需要结合任务证据复核"));
        } else if (!StringUtils.hasText(vo.getTraceId()) && vo.getAiCallLogId() == null && vo.getRunId() == null) {
            gate.setGateStatus("WARN");
            gate.setSuggestionStrength("WEAK");
            gate.setReasons(List.of("缺少可追踪的计划生成记录"));
        } else {
            gate.setGateStatus("PASS");
            gate.setSuggestionStrength("NORMAL");
            gate.setReasons(List.of("计划保留了 Agent Run、结果来源和追踪字段"));
        }
        return gate;
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
