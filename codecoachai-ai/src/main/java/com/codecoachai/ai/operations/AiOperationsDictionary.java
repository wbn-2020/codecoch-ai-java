package com.codecoachai.ai.operations;

import com.codecoachai.ai.domain.enums.AiFailureType;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class AiOperationsDictionary {

    private static final Pattern FAILURE_TYPE_PATTERN =
            Pattern.compile("(?i)(?:errorType|failureType)[\"'\\s:=]+([A-Z_]+)");
    private static final Pattern HTTP_STATUS_PATTERN =
            Pattern.compile("(?i)(?:httpStatus|HTTP)[\"'\\s:=]+(\\d{3})");

    private static final Map<String, SceneDescription> EXACT_SCENES = Map.ofEntries(
            Map.entry("ADMIN_MODEL_PROBE", scene("模型连通性检测", "模型治理")),
            Map.entry("PROMPT_VERSION_TEST", scene("提示词版本测试", "提示词治理")),
            Map.entry("PROMPT_RENDER_ONLY", scene("提示词渲染检查", "提示词治理")),
            Map.entry("INTERVIEW_QUESTION_GENERATE", scene("面试题生成", "模拟面试")),
            Map.entry("PROJECT_DEEP_DIVE_QUESTION", scene("项目深挖题生成", "模拟面试")),
            Map.entry("INTERVIEW_ANSWER_EVALUATE", scene("面试回答评估", "模拟面试")),
            Map.entry("INTERVIEW_FOLLOW_UP_GENERATE", scene("面试追问生成", "模拟面试")),
            Map.entry("INTERVIEW_REPORT_GENERATE", scene("面试报告生成", "模拟面试")),
            Map.entry("RESUME_STRUCTURED_PARSE", scene("简历结构化解析", "简历")),
            Map.entry("RESUME_OPTIMIZE", scene("简历优化", "简历")),
            Map.entry("RESUME_JOB_MATCH", scene("简历岗位匹配", "简历")),
            Map.entry("JOB_DESCRIPTION_PARSE", scene("职位描述解析", "求职")),
            Map.entry("SKILL_GAP_ANALYZE", scene("能力差距分析", "学习规划")),
            Map.entry("TARGETED_STUDY_PLAN_GENERATE", scene("针对性学习计划生成", "学习规划")),
            Map.entry("QUESTION_RECOMMENDATION", scene("题目推荐", "练习")),
            Map.entry("PRACTICE_ANSWER_REVIEW", scene("练习答案点评", "练习")),
            Map.entry("AGENT_DAILY_PLAN", scene("求职助手每日计划", "求职助手")),
            Map.entry("AGENT_REVIEW_GENERATE", scene("求职助手复盘", "求职助手")),
            Map.entry("CAREER_CAMPAIGN_REVIEW_GENERATE", scene("求职战役复盘", "求职助手")),
            Map.entry("CAREER_CAMPAIGN_PULSE_GENERATE", scene("求职战役脉搏分析", "求职助手")),
            Map.entry("APPLICATION_EVENT_REVIEW_GENERATE", scene("投递事件复盘", "投递管理")),
            Map.entry("INTERVIEW_PREPARATION_GENERATE", scene("面试准备建议", "投递管理")),
            Map.entry("EVIDENCE_USAGE_RESULT_DRAFT_V9", scene("证据使用结果草稿", "证据资产")),
            Map.entry("EVIDENCE_LEARNING_CANDIDATE_V9", scene("证据学习候选生成", "证据资产")),
            Map.entry("EVIDENCE_REUSE_MATERIAL_DRAFT_V9", scene("证据复用材料草稿", "证据资产")),
            Map.entry("EMBEDDING", scene("向量生成", "知识库"))
    );

    private AiOperationsDictionary() {
    }

    public static SceneDescription describeScene(String sceneCode) {
        String normalized = normalize(sceneCode);
        if (!StringUtils.hasText(normalized)) {
            return new SceneDescription("UNKNOWN", "未记录场景", "其他", false);
        }
        SceneDescription exact = EXACT_SCENES.get(normalized);
        if (exact != null) {
            return new SceneDescription(normalized, exact.label(), exact.category(), true);
        }
        if (normalized.contains("INTERVIEW")) {
            return inferred(normalized, "面试相关任务", "模拟面试");
        }
        if (normalized.contains("RESUME")) {
            return inferred(normalized, "简历相关任务", "简历");
        }
        if (normalized.contains("QUESTION") || normalized.contains("PRACTICE")) {
            return inferred(normalized, "题目与练习任务", "练习");
        }
        if (normalized.contains("PROMPT")) {
            return inferred(normalized, "提示词治理任务", "提示词治理");
        }
        if (normalized.contains("AGENT") || normalized.contains("CAMPAIGN")) {
            return inferred(normalized, "求职助手任务", "求职助手");
        }
        if (normalized.contains("APPLICATION") || normalized.contains("JOB")) {
            return inferred(normalized, "求职流程任务", "投递管理");
        }
        if (normalized.contains("EVIDENCE")) {
            return inferred(normalized, "证据资产任务", "证据资产");
        }
        if (normalized.contains("EMBEDDING") || normalized.contains("KNOWLEDGE")) {
            return inferred(normalized, "知识库任务", "知识库");
        }
        return new SceneDescription(normalized, "未登记场景", "其他", false);
    }

    public static FailureDescription describeFailure(
            String technicalError, Integer success, Integer status) {
        boolean explicitFailure = Integer.valueOf(0).equals(success) || Integer.valueOf(0).equals(status);
        if (!explicitFailure && !StringUtils.hasText(technicalError)) {
            return failure(AiFailureType.NONE, null);
        }
        AiFailureType type = extractFailureType(technicalError);
        Integer httpStatus = extractHttpStatus(technicalError);
        return failure(type, httpStatus);
    }

    private static FailureDescription failure(AiFailureType type, Integer httpStatus) {
        AiFailureType resolved = type == null ? AiFailureType.UNKNOWN_ERROR : type;
        return switch (resolved) {
            case NONE -> new FailureDescription("NONE", "调用成功", "本次调用已完成", "无需处理", null);
            case CONFIG_ERROR -> new FailureDescription(
                    resolved.name(), "模型配置不可用", "当前模型路由配置不完整或密钥无法使用",
                    "检查模型启用状态、供应商地址、模型标识和密钥配置", httpStatus);
            case TIMEOUT -> new FailureDescription(
                    resolved.name(), "调用超时", "上游模型在规定时间内没有完成响应",
                    "稍后重试；若持续发生，请检查供应商状态、网络与超时阈值", httpStatus);
            case HTTP_ERROR -> httpFailure(httpStatus);
            case EMPTY_RESPONSE -> new FailureDescription(
                    resolved.name(), "响应为空", "上游模型返回成功但没有可用内容",
                    "检查模型兼容性、响应格式和供应商返回内容", httpStatus);
            case PARSE_ERROR -> new FailureDescription(
                    resolved.name(), "响应解析失败", "模型已返回内容，但格式不符合当前业务契约",
                    "查看技术详情并检查提示词输出格式、JSON 结构与模型兼容性", httpStatus);
            case UNKNOWN_ERROR -> new FailureDescription(
                    resolved.name(), "未知调用异常", "系统尚未识别该失败类型",
                    "依据 traceId 查看技术详情和服务日志，确认后补充失败类型映射", httpStatus);
        };
    }

    private static FailureDescription httpFailure(Integer httpStatus) {
        if (httpStatus != null && (httpStatus == 401 || httpStatus == 403)) {
            return new FailureDescription(
                    AiFailureType.HTTP_ERROR.name(), "供应商鉴权失败", "上游拒绝了当前调用凭据",
                    "检查密钥是否有效、是否有目标模型权限，以及供应商账号状态", httpStatus);
        }
        if (httpStatus != null && httpStatus == 429) {
            return new FailureDescription(
                    AiFailureType.HTTP_ERROR.name(), "供应商限流或额度不足", "上游暂时拒绝更多请求",
                    "检查供应商额度和限流策略，降低并发或稍后重试", httpStatus);
        }
        if (httpStatus != null && httpStatus >= 500) {
            return new FailureDescription(
                    AiFailureType.HTTP_ERROR.name(), "供应商服务异常", "上游模型服务当前不可用",
                    "稍后重试，并结合供应商状态页和 traceId 排查", httpStatus);
        }
        return new FailureDescription(
                AiFailureType.HTTP_ERROR.name(), "供应商请求失败", "上游返回了非成功状态",
                "查看 HTTP 状态和技术详情，检查请求参数、模型权限及供应商状态", httpStatus);
    }

    private static AiFailureType extractFailureType(String technicalError) {
        if (!StringUtils.hasText(technicalError)) {
            return AiFailureType.UNKNOWN_ERROR;
        }
        Matcher matcher = FAILURE_TYPE_PATTERN.matcher(technicalError);
        if (matcher.find()) {
            try {
                return AiFailureType.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return AiFailureType.UNKNOWN_ERROR;
            }
        }
        String normalized = technicalError.toLowerCase(Locale.ROOT);
        if (normalized.contains("timeout") || normalized.contains("timed out")
                || normalized.contains("超时")) {
            return AiFailureType.TIMEOUT;
        }
        if (normalized.contains("empty response") || normalized.contains("响应为空")) {
            return AiFailureType.EMPTY_RESPONSE;
        }
        if (normalized.contains("parse") || normalized.contains("json")
                || normalized.contains("解析")) {
            return AiFailureType.PARSE_ERROR;
        }
        if (normalized.contains("config") || normalized.contains("api-key")
                || normalized.contains("configuration")) {
            return AiFailureType.CONFIG_ERROR;
        }
        if (normalized.contains("http") || normalized.contains("connect")) {
            return AiFailureType.HTTP_ERROR;
        }
        return AiFailureType.UNKNOWN_ERROR;
    }

    private static Integer extractHttpStatus(String technicalError) {
        if (!StringUtils.hasText(technicalError)) {
            return null;
        }
        Matcher matcher = HTTP_STATUS_PATTERN.matcher(technicalError);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static String normalize(String code) {
        return code == null ? null : code.trim()
                .replace('.', '_')
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }

    private static SceneDescription scene(String label, String category) {
        return new SceneDescription(null, label, category, true);
    }

    private static SceneDescription inferred(String code, String label, String category) {
        return new SceneDescription(code, label, category, false);
    }

    public record SceneDescription(String code, String label, String category, boolean registered) {
    }

    public record FailureDescription(
            String code,
            String label,
            String operatorMessage,
            String operatorSuggestion,
            Integer httpStatus) {
    }
}
