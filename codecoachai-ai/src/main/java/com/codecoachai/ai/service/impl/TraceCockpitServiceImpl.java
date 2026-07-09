package com.codecoachai.ai.service.impl;

import com.codecoachai.ai.domain.dto.TraceCockpitQueryDTO;
import com.codecoachai.ai.domain.vo.TraceCockpitResultVO;
import com.codecoachai.ai.domain.vo.TraceEdgeVO;
import com.codecoachai.ai.domain.vo.TraceGovernanceSuggestionVO;
import com.codecoachai.ai.domain.vo.TraceLinkVO;
import com.codecoachai.ai.domain.vo.TraceModuleStatusVO;
import com.codecoachai.ai.domain.vo.TraceNodeVO;
import com.codecoachai.ai.domain.vo.TraceOverviewVO;
import com.codecoachai.ai.domain.vo.TracePreviewItemVO;
import com.codecoachai.ai.domain.vo.TraceRawAccessStatusVO;
import com.codecoachai.ai.domain.vo.TraceRiskVO;
import com.codecoachai.ai.domain.vo.TraceTimelineVO;
import com.codecoachai.ai.service.TraceCockpitService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceCockpitServiceImpl implements TraceCockpitService {

    private static final String SOURCE_BACKEND = "BACKEND_AGGREGATED";
    private static final String RAW_PERMISSION = "admin:ai:log:raw:view";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_TIME_WINDOW_DAYS = 7;

    private static final String MOD_AI_CALL = "AI_CALL";
    private static final String MOD_AGENT_RUN = "AGENT_RUN";
    private static final String MOD_AGENT_TASK = "AGENT_TASK";
    private static final String MOD_AGENT_WEEK_PLAN = "AGENT_WEEK_PLAN";
    private static final String MOD_AGENT_WEEK_PLAN_ITEM = "AGENT_WEEK_PLAN_ITEM";
    private static final String MOD_ASYNC_TASK = "ASYNC_TASK";
    private static final String MOD_APPLICATION_PACKAGE = "APPLICATION_PACKAGE";
    private static final String MOD_INTERVIEW_SESSION = "INTERVIEW_SESSION";
    private static final String MOD_INTERVIEW_REPORT = "INTERVIEW_REPORT";
    private static final String MOD_INTERVIEW_VOICE = "INTERVIEW_VOICE";
    private static final String MOD_CONTEXT_USAGE_REFERENCE = "CONTEXT_USAGE_REFERENCE";
    private static final String MOD_KNOWLEDGE_DOCUMENT = "KNOWLEDGE_DOCUMENT";
    private static final String MOD_KNOWLEDGE_CHUNK = "KNOWLEDGE_CHUNK";
    private static final String MOD_MEMORY = "MEMORY";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d\\s-]{7,}\\d)(?!\\d)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?i)(api[_-]?key|authorization|token|secret|password|idempotency[_-]?key)\\s*[:=]\\s*[^,\\s}]+");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public TraceCockpitResultVO getTraceCockpit(TraceCockpitQueryDTO query) {
        TraceQuery seed = TraceQuery.from(query);
        LinkedHashMap<String, TraceNodeVO> nodesById = new LinkedHashMap<>();
        List<TraceModuleStatusVO> statuses = new ArrayList<>();
        List<TraceEdgeVO> edges = new ArrayList<>();

        if (!seed.hasSearchSeed()) {
            moduleDefinitions().forEach(item -> statuses.add(moduleStatus(item[0], item[1], "SKIPPED", null,
                    "Provide traceId, requestId, businessId, userId/time, messageId, or source id to aggregate.", null)));
            return buildResult(seed, nodesById, edges, statuses);
        }

        ModuleLoad aiCalls = loadModule(MOD_AI_CALL, "AI logs", () -> loadAiCallNodes(seed));
        addModule(nodesById, statuses, aiCalls);

        ModuleLoad agentRuns = loadModule(MOD_AGENT_RUN, "Agent runs", () -> loadAgentRunNodes(seed));
        addModule(nodesById, statuses, agentRuns);

        Set<Long> runIds = sourceIds(nodesById.values(), MOD_AGENT_RUN);
        ModuleLoad agentTasks = loadModule(MOD_AGENT_TASK, "Agent tasks", () -> loadAgentTaskNodes(seed, runIds));
        addModule(nodesById, statuses, agentTasks);

        ModuleLoad weekPlans = loadModule(MOD_AGENT_WEEK_PLAN, "Agent week plans", () -> loadAgentWeekPlanNodes(seed));
        addModule(nodesById, statuses, weekPlans);

        Set<Long> weekPlanIds = sourceIds(nodesById.values(), MOD_AGENT_WEEK_PLAN);
        ModuleLoad weekPlanItems = loadModule(MOD_AGENT_WEEK_PLAN_ITEM, "Agent week plan items",
                () -> loadAgentWeekPlanItemNodes(seed, weekPlanIds));
        addModule(nodesById, statuses, weekPlanItems);

        ModuleLoad asyncTasks = loadModule(MOD_ASYNC_TASK, "Async tasks", () -> loadAsyncTaskNodes(seed));
        addModule(nodesById, statuses, asyncTasks);

        ModuleLoad packages = loadModule(MOD_APPLICATION_PACKAGE, "Application packages", () -> loadApplicationPackageNodes(seed));
        addModule(nodesById, statuses, packages);

        BusinessSeed businessSeed = BusinessSeed.fromNodes(seed, nodesById.values());
        ModuleLoad sessions = loadModule(MOD_INTERVIEW_SESSION, "Interview sessions", () -> loadInterviewSessionNodes(seed, businessSeed));
        addModule(nodesById, statuses, sessions);

        Set<Long> sessionIds = sourceIds(nodesById.values(), MOD_INTERVIEW_SESSION);
        ModuleLoad reports = loadModule(MOD_INTERVIEW_REPORT, "Interview reports", () -> loadInterviewReportNodes(seed, sessionIds));
        addModule(nodesById, statuses, reports);

        ModuleLoad voices = loadModule(MOD_INTERVIEW_VOICE, "Interview voice submissions", () -> loadInterviewVoiceNodes(seed, sessionIds));
        addModule(nodesById, statuses, voices);

        ModuleLoad usageReferences = loadModule(MOD_CONTEXT_USAGE_REFERENCE, "Knowledge/memory usage references",
                () -> loadUsageReferenceNodes(seed, nodesById));
        addModule(nodesById, statuses, usageReferences);

        edges.addAll(buildEdges(nodesById.values()));
        edges.addAll(loadApplicationPackageEventEdges(seed, nodesById));
        edges.addAll(loadUsageReferenceEdges(seed, nodesById));
        edges.addAll(loadWeekPlanInfluenceEdges(seed, nodesById));
        return buildResult(seed, nodesById, edges, statuses);
    }

    private ModuleLoad loadModule(String module, String moduleName, Supplier<List<TraceNodeVO>> loader) {
        try {
            List<TraceNodeVO> nodes = loader.get();
            String status = nodes.isEmpty() ? "EMPTY" : "LOADED";
            return new ModuleLoad(module, moduleName, status, nodes, null, null);
        } catch (DataAccessException ex) {
            log.warn("Trace Cockpit module aggregation failed. module={}, error={}", module, safeError(ex));
            return new ModuleLoad(module, moduleName, "FAILED", List.of(), null, safeError(ex));
        } catch (RuntimeException ex) {
            log.warn("Trace Cockpit module aggregation failed. module={}, error={}", module, safeError(ex));
            return new ModuleLoad(module, moduleName, "FAILED", List.of(), null, safeError(ex));
        }
    }

    private void addModule(Map<String, TraceNodeVO> nodesById, List<TraceModuleStatusVO> statuses, ModuleLoad load) {
        statuses.add(moduleStatus(load.module, load.moduleName, load.status,
                "FAILED".equals(load.status) ? null : load.nodes.size(), load.message, load.errorMessage));
        load.nodes.forEach(node -> nodesById.putIfAbsent(node.getId(), node));
    }

    private List<TraceNodeVO> loadAiCallNodes(TraceQuery seed) {
        if (!seed.hasAiCallCriteria()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, user_id AS userId, scene, model_name AS modelName, model,
                       prompt_template_id AS promptTemplateId,
                       prompt_template_version_id AS promptTemplateVersionId,
                       prompt_version AS promptVersion, request_id AS requestId, trace_id AS traceId,
                       business_id AS businessId, elapsed_ms AS elapsedMs, cost_millis AS costMillis,
                       success, prompt_tokens AS promptTokens, completion_tokens AS completionTokens,
                       total_tokens AS totalTokens, status, error_message AS errorMessage,
                       route_trace AS routeTrace, estimated_cost AS estimatedCost, token_cost AS tokenCost,
                       created_at AS createdAt, updated_at AS updatedAt,
                       CASE WHEN request_prompt IS NOT NULL OR response_content IS NOT NULL
                              OR request_body IS NOT NULL OR response_body IS NOT NULL
                              OR input_variables_json IS NOT NULL OR model_params_json IS NOT NULL
                            THEN 1 ELSE 0 END AS rawFieldsAvailable,
                       CHAR_LENGTH(request_prompt) AS requestPromptLength,
                       SHA2(request_prompt, 256) AS requestPromptHash,
                       CHAR_LENGTH(response_content) AS responseContentLength,
                       SHA2(response_content, 256) AS responseContentHash,
                       CHAR_LENGTH(request_body) AS requestBodyLength,
                       SHA2(request_body, 256) AS requestBodyHash,
                       CHAR_LENGTH(response_body) AS responseBodyLength,
                       SHA2(response_body, 256) AS responseBodyHash,
                       CHAR_LENGTH(input_variables_json) AS inputVariablesLength,
                       SHA2(input_variables_json, 256) AS inputVariablesHash,
                       CHAR_LENGTH(model_params_json) AS modelParamsLength,
                       SHA2(model_params_json, 256) AS modelParamsHash
                  FROM ai_call_log
                 WHERE deleted = 0
                """);
        appendEquals(sql, args, "trace_id", seed.traceId);
        appendEquals(sql, args, "request_id", seed.requestId);
        appendEquals(sql, args, "business_id", seed.businessId);
        appendEquals(sql, args, "user_id", seed.userId);
        appendEquals(sql, args, "scene", seed.scene);
        if (seed.agentRunId != null) {
            sql.append(" AND id IN (SELECT ai_call_log_id FROM agent_run WHERE id = ? AND deleted = 0 AND ai_call_log_id IS NOT NULL)");
            args.add(seed.agentRunId);
        }
        appendTimeRange(sql, args, "created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(seed.limit);

        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(row -> aiCallNode(row, seed))
                .toList();
    }

    private TraceNodeVO aiCallNode(Map<String, Object> row, TraceQuery seed) {
        Long id = asLong(row, "id");
        String traceId = asString(row, "traceId");
        String requestId = asString(row, "requestId");
        boolean fallback = containsFallback(asString(row, "routeTrace"));
        String status = normalizeStatus(firstNonNull(row.get("success"), row.get("status")), fallback);

        TraceNodeVO node = baseNode("ai-" + id, MOD_AI_CALL, "ai_call_log", id, firstText(asString(row, "scene"), "AI call " + id),
                status, seed, traceId, requestId, asString(row, "businessId"), null, null, asLong(row, "userId"),
                asDateTime(row, "createdAt"), association(seed, traceId, requestId, null, null, null, id));
        node.setSummary("AI call " + safeText(asString(row, "scene"), 40) + " / " + safeText(firstText(asString(row, "modelName"), asString(row, "model")), 60));
        node.setPreview(maskText(asString(row, "errorMessage"), 160));
        node.setContentHash(firstText(asString(row, "responseContentHash"), asString(row, "requestPromptHash"), asString(row, "responseBodyHash")));
        node.setContentLength(firstInt(row, "responseContentLength", "requestPromptLength", "responseBodyLength"));
        node.setRawAccess(rawAccess(asBoolean(row, "rawFieldsAvailable")));
        addPreview(node, "request prompt", null, asString(row, "requestPromptHash"), asInteger(row, "requestPromptLength"));
        addPreview(node, "response content", null, asString(row, "responseContentHash"), asInteger(row, "responseContentLength"));
        addPreview(node, "request body", null, asString(row, "requestBodyHash"), asInteger(row, "requestBodyLength"));
        addPreview(node, "response body", null, asString(row, "responseBodyHash"), asInteger(row, "responseBodyLength"));
        addPreview(node, "input variables", null, asString(row, "inputVariablesHash"), asInteger(row, "inputVariablesLength"));
        addPreview(node, "model params", null, asString(row, "modelParamsHash"), asInteger(row, "modelParamsLength"));
        node.getLinks().add(link("AI log", "/admin/ai/logs", queryMap("traceId", traceId, "requestId", requestId, "businessId", asString(row, "businessId"), "aiCallLogId", id)));
        if (asLong(row, "promptTemplateId") != null) {
            node.getLinks().add(link("Prompt regression", "/admin/ai/prompt-regression",
                    queryMap("promptTemplateId", asLong(row, "promptTemplateId"), "traceId", traceId)));
        }
        putMeta(node, "scene", asString(row, "scene"));
        putMeta(node, "modelName", firstText(asString(row, "modelName"), asString(row, "model")));
        putMeta(node, "routeTrace", asString(row, "routeTrace"));
        putMeta(node, "resultSource", fallback ? "FALLBACK_INFERRED" : "REAL");
        putMeta(node, "fallback", fallback);
        putMeta(node, "elapsedMs", firstNonNull(asLong(row, "elapsedMs"), asLong(row, "costMillis")));
        putMeta(node, "totalTokens", asLong(row, "totalTokens"));
        putMeta(node, "promptTokens", asLong(row, "promptTokens"));
        putMeta(node, "completionTokens", asLong(row, "completionTokens"));
        putMeta(node, "promptTemplateId", asLong(row, "promptTemplateId"));
        putMeta(node, "promptTemplateVersionId", asLong(row, "promptTemplateVersionId"));
        putMeta(node, "tokenCost", asBigDecimal(row, "tokenCost"));
        putMeta(node, "estimatedCost", asBigDecimal(row, "estimatedCost"));
        return node;
    }

    private List<TraceNodeVO> loadAgentRunNodes(TraceQuery seed) {
        if (!seed.hasAgentRunCriteria()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, user_id AS userId, agent_type AS agentType, target_job_id AS targetJobId,
                       plan_date AS planDate, trigger_type AS triggerType, status, prompt_type AS promptType,
                       prompt_version_id AS promptVersionId, model_name AS modelName, trace_id AS traceId,
                       ai_call_log_id AS aiCallLogId, result_source AS resultSource,
                       token_input AS tokenInput, token_output AS tokenOutput, duration_ms AS durationMs,
                       error_code AS errorCode, error_message AS errorMessage,
                       started_at AS startedAt, finished_at AS finishedAt, created_at AS createdAt, updated_at AS updatedAt,
                       CASE WHEN input_snapshot_json IS NOT NULL OR output_json IS NOT NULL OR raw_output_text IS NOT NULL
                            THEN 1 ELSE 0 END AS rawFieldsAvailable,
                       CHAR_LENGTH(input_snapshot_json) AS inputSnapshotLength,
                       SHA2(input_snapshot_json, 256) AS inputSnapshotHash,
                       CHAR_LENGTH(output_json) AS outputLength,
                       SHA2(output_json, 256) AS outputHash,
                       CHAR_LENGTH(raw_output_text) AS rawOutputLength,
                       SHA2(raw_output_text, 256) AS rawOutputHash
                  FROM agent_run
                 WHERE deleted = 0
                """);
        appendEquals(sql, args, "id", seed.agentRunId);
        appendEquals(sql, args, "trace_id", seed.traceId);
        appendEquals(sql, args, "user_id", seed.userId);
        if (seed.isBusinessType("AGENT_RUN") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "id", seed.businessIdAsLong());
        }
        if (seed.isBusinessType("TARGET_JOB") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "target_job_id", seed.businessIdAsLong());
        }
        appendTimeRange(sql, args, "created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(seed.limit);

        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(row -> agentRunNode(row, seed))
                .toList();
    }

    private TraceNodeVO agentRunNode(Map<String, Object> row, TraceQuery seed) {
        Long id = asLong(row, "id");
        String traceId = asString(row, "traceId");
        boolean fallback = containsFallback(asString(row, "resultSource"));
        TraceNodeVO node = baseNode("agent-run-" + id, MOD_AGENT_RUN, "agent_run", id,
                firstText(asString(row, "agentType"), "Agent run " + id), normalizeStatus(asString(row, "status"), fallback),
                seed, traceId, null, null, null, null, asLong(row, "userId"),
                firstDateTime(row, "startedAt", "createdAt"), association(seed, traceId, null, null, null, null, id));
        node.setSummary("Agent run " + safeText(asString(row, "agentType"), 60) + " / " + safeText(asString(row, "status"), 40));
        node.setPreview(maskText(asString(row, "errorMessage"), 160));
        node.setContentHash(firstText(asString(row, "outputHash"), asString(row, "inputSnapshotHash"), asString(row, "rawOutputHash")));
        node.setContentLength(firstInt(row, "outputLength", "inputSnapshotLength", "rawOutputLength"));
        node.setRawAccess(rawAccess(asBoolean(row, "rawFieldsAvailable")));
        addPreview(node, "input snapshot", null, asString(row, "inputSnapshotHash"), asInteger(row, "inputSnapshotLength"));
        addPreview(node, "output json", null, asString(row, "outputHash"), asInteger(row, "outputLength"));
        addPreview(node, "raw output", null, asString(row, "rawOutputHash"), asInteger(row, "rawOutputLength"));
        node.getLinks().add(link("Agent Run", "/admin/agent/runs", queryMap("traceId", traceId, "runId", id)));
        putMeta(node, "agentType", asString(row, "agentType"));
        putMeta(node, "triggerType", asString(row, "triggerType"));
        putMeta(node, "resultSource", asString(row, "resultSource"));
        putMeta(node, "fallback", fallback);
        putMeta(node, "durationMs", asLong(row, "durationMs"));
        putMeta(node, "tokenInput", asLong(row, "tokenInput"));
        putMeta(node, "tokenOutput", asLong(row, "tokenOutput"));
        putMeta(node, "totalTokens", sum(asLong(row, "tokenInput"), asLong(row, "tokenOutput")));
        putMeta(node, "aiCallLogId", asLong(row, "aiCallLogId"));
        putMeta(node, "targetJobId", asLong(row, "targetJobId"));
        putMeta(node, "promptType", asString(row, "promptType"));
        putMeta(node, "promptVersionId", asLong(row, "promptVersionId"));
        putMeta(node, "modelName", asString(row, "modelName"));
        return node;
    }

    private List<TraceNodeVO> loadAgentTaskNodes(TraceQuery seed, Set<Long> runIds) {
        if (!seed.hasAgentTaskCriteria(runIds)) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT t.id, t.user_id AS userId, t.agent_run_id AS agentRunId,
                       t.target_job_id AS targetJobId, t.task_type AS taskType, t.title,
                       t.priority, t.estimated_minutes AS estimatedMinutes,
                       t.related_biz_type AS relatedBizType, t.related_biz_id AS relatedBizId,
                       t.status, t.skip_reason AS skipReason, t.defer_reason AS deferReason,
                       t.due_date AS dueDate, t.started_at AS startedAt, t.completed_at AS completedAt,
                       t.deferred_at AS deferredAt, t.skipped_at AS skippedAt,
                       t.created_at AS createdAt, t.updated_at AS updatedAt,
                       r.trace_id AS traceId, r.result_source AS resultSource, r.ai_call_log_id AS aiCallLogId
                  FROM agent_task t
                  JOIN agent_run r ON r.id = t.agent_run_id AND r.deleted = 0
                 WHERE t.deleted = 0
                """);
        if (!runIds.isEmpty()) {
            appendIn(sql, args, "t.agent_run_id", runIds);
        }
        appendEquals(sql, args, "t.user_id", seed.userId);
        appendEquals(sql, args, "r.trace_id", seed.traceId);
        if (seed.isBusinessType("AGENT_TASK") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "t.id", seed.businessIdAsLong());
        }
        if (StringUtils.hasText(seed.bizType) && StringUtils.hasText(seed.bizId)) {
            appendEquals(sql, args, "t.related_biz_type", seed.bizType);
            appendEquals(sql, args, "t.related_biz_id", seed.bizIdAsLong());
        }
        appendTimeRange(sql, args, "t.created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY t.created_at DESC LIMIT ?");
        args.add(seed.limit);

        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(row -> agentTaskNode(row, seed))
                .toList();
    }

    private TraceNodeVO agentTaskNode(Map<String, Object> row, TraceQuery seed) {
        Long id = asLong(row, "id");
        String traceId = asString(row, "traceId");
        String relatedBizType = asString(row, "relatedBizType");
        String relatedBizId = asString(row, "relatedBizId");
        boolean fallback = containsFallback(asString(row, "resultSource"));
        TraceNodeVO node = baseNode("agent-task-" + id, MOD_AGENT_TASK, "agent_task", id,
                firstText(asString(row, "title"), "Agent task " + id), normalizeStatus(asString(row, "status"), fallback),
                seed, traceId, null, null, relatedBizType, relatedBizId, asLong(row, "userId"),
                firstDateTime(row, "startedAt", "createdAt"),
                association(seed, traceId, null, null, relatedBizType, relatedBizId, id));
        node.setSummary("Agent task " + safeText(asString(row, "taskType"), 50) + " / " + safeText(asString(row, "status"), 30));
        node.setPreview(maskText(firstText(asString(row, "skipReason"), asString(row, "deferReason")), 120));
        node.setRawAccess(rawAccess(false));
        addPreview(node, "task title", maskText(asString(row, "title"), 120), null, null);
        addPreview(node, "skip/defer reason", maskText(firstText(asString(row, "skipReason"), asString(row, "deferReason")), 120), null, null);
        node.getLinks().add(link("Agent Run", "/admin/agent/runs", queryMap("traceId", traceId, "runId", asLong(row, "agentRunId"))));
        putMeta(node, "agentRunId", asLong(row, "agentRunId"));
        putMeta(node, "taskType", asString(row, "taskType"));
        putMeta(node, "priority", asString(row, "priority"));
        putMeta(node, "estimatedMinutes", asLong(row, "estimatedMinutes"));
        putMeta(node, "targetJobId", asLong(row, "targetJobId"));
        putMeta(node, "relatedBizType", relatedBizType);
        putMeta(node, "relatedBizId", relatedBizId);
        putMeta(node, "resultSource", asString(row, "resultSource"));
        putMeta(node, "fallback", fallback);
        putMeta(node, "aiCallLogId", asLong(row, "aiCallLogId"));
        return node;
    }

    private List<TraceNodeVO> loadAgentWeekPlanNodes(TraceQuery seed) {
        if (!seed.hasAgentWeekPlanCriteria()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, user_id AS userId, target_job_id AS targetJobId, agent_run_id AS agentRunId,
                       week_start_date AS weekStartDate, week_end_date AS weekEndDate, plan_status AS planStatus,
                       summary, trace_id AS traceId, result_source AS resultSource, fallback,
                       fallback_reason AS fallbackReason, snapshot_version AS snapshotVersion,
                       generated_at AS generatedAt, refreshed_at AS refreshedAt,
                       created_at AS createdAt, updated_at AS updatedAt,
                       CASE WHEN focus_json IS NOT NULL THEN 1 ELSE 0 END AS rawFieldsAvailable,
                       CHAR_LENGTH(focus_json) AS focusLength, SHA2(focus_json, 256) AS focusHash
                  FROM agent_week_plan
                 WHERE deleted = 0
                """);
        appendEquals(sql, args, "trace_id", seed.traceId);
        appendEquals(sql, args, "user_id", seed.userId);
        if (seed.isBusinessType(MOD_AGENT_WEEK_PLAN) && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "id", seed.businessIdAsLong());
        }
        if (seed.isBusinessType(MOD_AGENT_RUN) && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "agent_run_id", seed.businessIdAsLong());
        }
        if (seed.agentRunId != null) {
            appendEquals(sql, args, "agent_run_id", seed.agentRunId);
        }
        if (seed.isBusinessType("TARGET_JOB") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "target_job_id", seed.businessIdAsLong());
        }
        appendTimeRange(sql, args, "created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY updated_at DESC LIMIT ?");
        args.add(seed.limit);

        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(row -> agentWeekPlanNode(row, seed))
                .toList();
    }

    private TraceNodeVO agentWeekPlanNode(Map<String, Object> row, TraceQuery seed) {
        Long id = asLong(row, "id");
        String traceId = asString(row, "traceId");
        boolean fallback = Boolean.TRUE.equals(asBoolean(row, "fallback")) || containsFallback(asString(row, "resultSource"));
        TraceNodeVO node = baseNode("agent-week-plan-" + id, MOD_AGENT_WEEK_PLAN, "agent_week_plan", id,
                "Agent week plan " + id, normalizeStatus(asString(row, "planStatus"), fallback),
                seed, traceId, null, String.valueOf(id), MOD_AGENT_WEEK_PLAN, String.valueOf(id), asLong(row, "userId"),
                firstDateTime(row, "refreshedAt", "generatedAt", "createdAt"),
                association(seed, traceId, null, null, MOD_AGENT_WEEK_PLAN, String.valueOf(id), id));
        node.setSummary(maskText(asString(row, "summary"), 180));
        node.setPreview(maskText(firstText(asString(row, "fallbackReason"), asString(row, "summary")), 160));
        node.setContentHash(asString(row, "focusHash"));
        node.setContentLength(asInteger(row, "focusLength"));
        node.setRawAccess(rawAccess(asBoolean(row, "rawFieldsAvailable")));
        addPreview(node, "focus snapshot", null, asString(row, "focusHash"), asInteger(row, "focusLength"));
        node.getLinks().add(link("Agent week plan", "/agent/today", queryMap("weekPlanId", id, "traceId", traceId)));
        putMeta(node, "targetJobId", asLong(row, "targetJobId"));
        putMeta(node, "agentRunId", asLong(row, "agentRunId"));
        putMeta(node, "weekStartDate", asString(row, "weekStartDate"));
        putMeta(node, "weekEndDate", asString(row, "weekEndDate"));
        putMeta(node, "resultSource", asString(row, "resultSource"));
        putMeta(node, "fallback", fallback);
        putMeta(node, "fallbackReason", maskText(asString(row, "fallbackReason"), 120));
        putMeta(node, "snapshotVersion", asLong(row, "snapshotVersion"));
        return node;
    }

    private List<TraceNodeVO> loadAgentWeekPlanItemNodes(TraceQuery seed, Set<Long> weekPlanIds) {
        if (!seed.hasAgentWeekPlanItemCriteria(weekPlanIds)) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.user_id AS userId, i.week_plan_id AS weekPlanId,
                       i.agent_task_id AS agentTaskId, i.layer, i.action_type AS actionType,
                       i.title, i.related_biz_type AS relatedBizType, i.related_biz_id AS relatedBizId,
                       i.priority, i.confidence, i.trust_status AS trustStatus, i.fallback,
                       i.fallback_reason AS fallbackReason, i.item_status AS itemStatus,
                       i.planned_date AS plannedDate, i.due_date AS dueDate,
                       i.sort_order AS sortOrder, i.created_at AS createdAt, i.updated_at AS updatedAt,
                       COALESCE(i.trace_id, p.trace_id) AS traceId, i.trace_id AS itemTraceId,
                       p.trace_id AS planTraceId, p.target_job_id AS targetJobId, p.agent_run_id AS agentRunId,
                       i.snapshot_version AS snapshotVersion, p.snapshot_version AS planSnapshotVersion,
                       i.confidence_level AS confidenceLevel, i.sample_insufficient AS sampleInsufficient,
                       i.sample_warning AS sampleWarning,
                       CASE WHEN i.evidence_json IS NOT NULL THEN 1 ELSE 0 END AS rawFieldsAvailable,
                       CHAR_LENGTH(i.evidence_json) AS evidenceLength, SHA2(i.evidence_json, 256) AS evidenceHash
                  FROM agent_week_plan_item i
                  JOIN agent_week_plan p ON p.id = i.week_plan_id AND p.deleted = 0
                 WHERE i.deleted = 0
                """);
        appendIn(sql, args, "i.week_plan_id", weekPlanIds);
        if (StringUtils.hasText(seed.traceId)) {
            sql.append(" AND (i.trace_id = ? OR p.trace_id = ?)");
            args.add(seed.traceId);
            args.add(seed.traceId);
        }
        appendEquals(sql, args, "i.user_id", seed.userId);
        if (seed.isBusinessType(MOD_AGENT_WEEK_PLAN_ITEM) && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "i.id", seed.businessIdAsLong());
        }
        if (seed.isBusinessType(MOD_AGENT_WEEK_PLAN) && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "i.week_plan_id", seed.businessIdAsLong());
        }
        if (seed.isBusinessType(MOD_AGENT_TASK) && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "i.agent_task_id", seed.businessIdAsLong());
        }
        if (StringUtils.hasText(seed.bizType) && StringUtils.hasText(seed.bizId)) {
            appendEquals(sql, args, "i.related_biz_type", seed.bizType);
            appendEquals(sql, args, "i.related_biz_id", seed.bizIdAsLong());
        }
        appendTimeRange(sql, args, "i.created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY i.planned_date ASC, i.sort_order ASC, i.id ASC LIMIT ?");
        args.add(seed.limit);

        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(row -> agentWeekPlanItemNode(row, seed))
                .toList();
    }

    private TraceNodeVO agentWeekPlanItemNode(Map<String, Object> row, TraceQuery seed) {
        Long id = asLong(row, "id");
        String traceId = asString(row, "traceId");
        String relatedBizType = asString(row, "relatedBizType");
        String relatedBizId = asString(row, "relatedBizId");
        boolean fallback = Boolean.TRUE.equals(asBoolean(row, "fallback"));
        TraceNodeVO node = baseNode("agent-week-plan-item-" + id, MOD_AGENT_WEEK_PLAN_ITEM, "agent_week_plan_item", id,
                firstText(asString(row, "title"), "Agent week plan item " + id),
                normalizeStatus(asString(row, "itemStatus"), fallback), seed, traceId, null, String.valueOf(id),
                relatedBizType, relatedBizId, asLong(row, "userId"), firstDateTime(row, "plannedDate", "createdAt"),
                association(seed, traceId, null, null, relatedBizType, relatedBizId, id));
        node.setSummary("Week plan item " + safeText(asString(row, "layer"), 40)
                + " / " + safeText(asString(row, "itemStatus"), 40));
        node.setPreview(maskText(asString(row, "fallbackReason"), 160));
        node.setContentHash(asString(row, "evidenceHash"));
        node.setContentLength(asInteger(row, "evidenceLength"));
        node.setRawAccess(rawAccess(asBoolean(row, "rawFieldsAvailable")));
        addPreview(node, "item evidence", null, asString(row, "evidenceHash"), asInteger(row, "evidenceLength"));
        node.getLinks().add(link("Agent today", "/agent/today", queryMap("weekPlanId", asLong(row, "weekPlanId"), "taskId", asLong(row, "agentTaskId"))));
        putMeta(node, "weekPlanId", asLong(row, "weekPlanId"));
        putMeta(node, "agentTaskId", asLong(row, "agentTaskId"));
        putMeta(node, "agentRunId", asLong(row, "agentRunId"));
        putMeta(node, "targetJobId", asLong(row, "targetJobId"));
        putMeta(node, "snapshotVersion", asLong(row, "snapshotVersion"));
        putMeta(node, "planSnapshotVersion", asLong(row, "planSnapshotVersion"));
        putMeta(node, "itemTraceId", asString(row, "itemTraceId"));
        putMeta(node, "planTraceId", asString(row, "planTraceId"));
        putMeta(node, "layer", asString(row, "layer"));
        putMeta(node, "actionType", asString(row, "actionType"));
        putMeta(node, "priority", asString(row, "priority"));
        putMeta(node, "confidence", asBigDecimal(row, "confidence"));
        putMeta(node, "confidenceLevel", asString(row, "confidenceLevel"));
        putMeta(node, "sampleInsufficient", asBoolean(row, "sampleInsufficient"));
        putMeta(node, "sampleWarning", maskText(asString(row, "sampleWarning"), 120));
        putMeta(node, "trustStatus", asString(row, "trustStatus"));
        putMeta(node, "fallback", fallback);
        putMeta(node, "relatedBizType", relatedBizType);
        putMeta(node, "relatedBizId", relatedBizId);
        return node;
    }

    private List<TraceNodeVO> loadAsyncTaskNodes(TraceQuery seed) {
        if (!seed.hasAsyncTaskCriteria()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, message_id AS messageId, biz_type AS bizType, biz_id AS bizId,
                       user_id AS userId, trace_id AS traceId, status, retry_count AS retryCount,
                       max_retry AS maxRetry, failure_reason AS failureReason,
                       started_at AS startedAt, completed_at AS completedAt, created_at AS createdAt, updated_at AS updatedAt,
                       CASE WHEN payload IS NOT NULL OR `result` IS NOT NULL THEN 1 ELSE 0 END AS rawFieldsAvailable,
                       CHAR_LENGTH(payload) AS payloadLength, SHA2(payload, 256) AS payloadHash,
                       CHAR_LENGTH(`result`) AS resultLength, SHA2(`result`, 256) AS resultHash
                  FROM async_task
                 WHERE deleted = 0
                """);
        appendEquals(sql, args, "id", seed.asyncTaskId);
        appendEquals(sql, args, "message_id", seed.messageId);
        appendEquals(sql, args, "trace_id", seed.traceId);
        appendEquals(sql, args, "user_id", seed.userId);
        if (StringUtils.hasText(seed.bizType) && StringUtils.hasText(seed.bizId)) {
            appendEquals(sql, args, "biz_type", seed.bizType);
            appendEquals(sql, args, "biz_id", seed.bizId);
        } else if (StringUtils.hasText(seed.businessType) && StringUtils.hasText(seed.businessId)) {
            appendEquals(sql, args, "biz_type", seed.businessType);
            appendEquals(sql, args, "biz_id", seed.businessId);
        }
        appendTimeRange(sql, args, "created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(seed.limit);

        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(row -> asyncTaskNode(row, seed))
                .toList();
    }

    private TraceNodeVO asyncTaskNode(Map<String, Object> row, TraceQuery seed) {
        Long id = asLong(row, "id");
        String traceId = asString(row, "traceId");
        String messageId = asString(row, "messageId");
        String bizType = asString(row, "bizType");
        String bizId = asString(row, "bizId");
        TraceNodeVO node = baseNode("async-task-" + id, MOD_ASYNC_TASK, "async_task", id,
                firstText(bizType, "Async task " + id), normalizeStatus(asString(row, "status"), false),
                seed, traceId, null, null, bizType, bizId, asLong(row, "userId"),
                firstDateTime(row, "startedAt", "createdAt"),
                association(seed, traceId, null, messageId, bizType, bizId, id));
        node.setMessageId(messageId);
        node.setSummary("Async task " + safeText(bizType, 60) + " / " + safeText(asString(row, "status"), 30));
        node.setPreview(maskText(asString(row, "failureReason"), 160));
        node.setContentHash(firstText(asString(row, "resultHash"), asString(row, "payloadHash")));
        node.setContentLength(firstInt(row, "resultLength", "payloadLength"));
        node.setRawAccess(rawAccess(asBoolean(row, "rawFieldsAvailable")));
        addPreview(node, "payload", null, asString(row, "payloadHash"), asInteger(row, "payloadLength"));
        addPreview(node, "result", null, asString(row, "resultHash"), asInteger(row, "resultLength"));
        node.getLinks().add(link("Async task", "/admin/async-tasks",
                queryMap("traceId", traceId, "messageId", messageId, "bizType", bizType, "bizId", bizId, "taskId", id)));
        putMeta(node, "bizType", bizType);
        putMeta(node, "bizId", bizId);
        putMeta(node, "retryCount", asLong(row, "retryCount"));
        putMeta(node, "maxRetryCount", asLong(row, "maxRetry"));
        return node;
    }

    private List<TraceNodeVO> loadApplicationPackageNodes(TraceQuery seed) {
        if (!seed.hasApplicationPackageCriteria()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, user_id AS userId, package_no AS packageNo, target_job_id AS targetJobId,
                       jd_analysis_id AS jdAnalysisId, resume_id AS resumeId, resume_version_id AS resumeVersionId,
                       match_report_id AS matchReportId, application_id AS applicationId,
                       company_name AS companyName, job_title AS jobTitle,
                       readiness_level AS readinessLevel, readiness_score AS readinessScore,
                       readiness_reason AS readinessReason, package_status AS packageStatus,
                       trace_id AS traceId, result_source AS resultSource, fallback,
                       fallback_reason AS fallbackReason, snapshot_version AS snapshotVersion,
                       refreshed_at AS refreshedAt, created_at AS createdAt, updated_at AS updatedAt,
                       CASE WHEN snapshot_json IS NOT NULL OR checklist_json IS NOT NULL OR actions_json IS NOT NULL
                              OR project_evidence_ids_json IS NOT NULL THEN 1 ELSE 0 END AS rawFieldsAvailable,
                       CHAR_LENGTH(snapshot_json) AS snapshotLength, SHA2(snapshot_json, 256) AS snapshotHash,
                       CHAR_LENGTH(checklist_json) AS checklistLength, SHA2(checklist_json, 256) AS checklistHash,
                       CHAR_LENGTH(actions_json) AS actionsLength, SHA2(actions_json, 256) AS actionsHash,
                       CHAR_LENGTH(project_evidence_ids_json) AS evidenceIdsLength,
                       SHA2(project_evidence_ids_json, 256) AS evidenceIdsHash
                  FROM job_application_package
                 WHERE deleted = 0
                """);
        appendEquals(sql, args, "trace_id", seed.traceId);
        appendEquals(sql, args, "user_id", seed.userId);
        if (seed.isBusinessType("APPLICATION_PACKAGE", "JOB_APPLICATION_PACKAGE") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "id", seed.businessIdAsLong());
        } else if (seed.isBusinessType("JOB_APPLICATION", "APPLICATION") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "application_id", seed.businessIdAsLong());
        } else if (seed.isBusinessType("MATCH_REPORT", "RESUME_MATCH_REPORT") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "match_report_id", seed.businessIdAsLong());
        } else if (seed.isBusinessType("JD_ANALYSIS", "JOB_DESCRIPTION_ANALYSIS") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "jd_analysis_id", seed.businessIdAsLong());
        } else if (seed.isBusinessType("RESUME_VERSION") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "resume_version_id", seed.businessIdAsLong());
        } else if (seed.isBusinessType("TARGET_JOB") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "target_job_id", seed.businessIdAsLong());
        }
        appendTimeRange(sql, args, "created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY updated_at DESC LIMIT ?");
        args.add(seed.limit);

        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(row -> applicationPackageNode(row, seed))
                .toList();
    }

    private TraceNodeVO applicationPackageNode(Map<String, Object> row, TraceQuery seed) {
        Long id = asLong(row, "id");
        String traceId = asString(row, "traceId");
        boolean fallback = Boolean.TRUE.equals(asBoolean(row, "fallback")) || containsFallback(asString(row, "resultSource"));
        TraceNodeVO node = baseNode("application-package-" + id, MOD_APPLICATION_PACKAGE, "job_application_package", id,
                firstText(asString(row, "jobTitle"), asString(row, "packageNo"), "Application package " + id),
                normalizeStatus(asString(row, "packageStatus"), fallback), seed, traceId, null, String.valueOf(id),
                MOD_APPLICATION_PACKAGE, String.valueOf(id), asLong(row, "userId"), firstDateTime(row, "refreshedAt", "createdAt"),
                association(seed, traceId, null, null, MOD_APPLICATION_PACKAGE, String.valueOf(id), id));
        node.setSummary(safeText(firstText(asString(row, "companyName"), "Target company"), 80)
                + " / " + safeText(firstText(asString(row, "jobTitle"), "Target job"), 100));
        node.setPreview(maskText(firstText(asString(row, "readinessReason"), asString(row, "fallbackReason")), 160));
        node.setContentHash(firstText(asString(row, "snapshotHash"), asString(row, "checklistHash"), asString(row, "actionsHash")));
        node.setContentLength(firstInt(row, "snapshotLength", "checklistLength", "actionsLength"));
        node.setRawAccess(rawAccess(asBoolean(row, "rawFieldsAvailable")));
        addPreview(node, "snapshot", null, asString(row, "snapshotHash"), asInteger(row, "snapshotLength"));
        addPreview(node, "checklist", null, asString(row, "checklistHash"), asInteger(row, "checklistLength"));
        addPreview(node, "actions", null, asString(row, "actionsHash"), asInteger(row, "actionsLength"));
        addPreview(node, "project evidence ids", null, asString(row, "evidenceIdsHash"), asInteger(row, "evidenceIdsLength"));
        node.getLinks().add(link("Application package", "/application-packages/" + id, queryMap()));
        putMeta(node, "packageNo", asString(row, "packageNo"));
        putMeta(node, "companyName", asString(row, "companyName"));
        putMeta(node, "jobTitle", asString(row, "jobTitle"));
        putMeta(node, "applicationPackageId", id);
        putMeta(node, "targetJobId", asLong(row, "targetJobId"));
        putMeta(node, "jdAnalysisId", asLong(row, "jdAnalysisId"));
        putMeta(node, "resumeId", asLong(row, "resumeId"));
        putMeta(node, "resumeVersionId", asLong(row, "resumeVersionId"));
        putMeta(node, "matchReportId", asLong(row, "matchReportId"));
        putMeta(node, "applicationId", asLong(row, "applicationId"));
        putMeta(node, "readinessLevel", asString(row, "readinessLevel"));
        putMeta(node, "readinessScore", asLong(row, "readinessScore"));
        putMeta(node, "snapshotVersion", asLong(row, "snapshotVersion"));
        putMeta(node, "resultSource", asString(row, "resultSource"));
        putMeta(node, "fallback", fallback);
        putMeta(node, "fallbackReason", maskText(asString(row, "fallbackReason"), 120));
        return node;
    }

    private List<TraceNodeVO> loadInterviewSessionNodes(TraceQuery seed, BusinessSeed businessSeed) {
        if (!seed.hasInterviewSessionCriteria(businessSeed)) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, user_id AS userId, application_id AS applicationId,
                       application_package_id AS applicationPackageId, resume_id AS resumeId,
                       resume_version_id AS resumeVersionId, target_job_id AS targetJobId,
                       jd_analysis_id AS jdAnalysisId, skill_profile_id AS skillProfileId, match_report_id AS matchReportId,
                       mode, title, target_position AS targetPosition, training_scene AS trainingScene,
                       target_skill_domain AS targetSkillDomain, status, report_status AS reportStatus,
                       answered_question_count AS answeredQuestionCount, max_question_count AS maxQuestionCount,
                       total_score AS totalScore, start_time AS startTime, end_time AS endTime,
                       failure_reason AS failureReason, created_at AS createdAt, updated_at AS updatedAt,
                       CASE WHEN training_context_summary IS NOT NULL THEN 1 ELSE 0 END AS rawFieldsAvailable,
                       CHAR_LENGTH(training_context_summary) AS trainingContextLength,
                       SHA2(training_context_summary, 256) AS trainingContextHash
                  FROM interview_session
                 WHERE deleted = 0
                """);
        appendEquals(sql, args, "user_id", seed.userId);
        if (seed.isBusinessType("INTERVIEW_SESSION", "INTERVIEW") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "id", seed.businessIdAsLong());
        }
        if (seed.isBusinessType("JOB_APPLICATION", "APPLICATION") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "application_id", seed.businessIdAsLong());
        }
        appendBusinessAny(sql, args, Map.of(
                "application_package_id", businessSeed.applicationPackageIds,
                "application_id", businessSeed.applicationIds,
                "jd_analysis_id", businessSeed.jdAnalysisIds,
                "resume_version_id", businessSeed.resumeVersionIds,
                "match_report_id", businessSeed.matchReportIds,
                "target_job_id", businessSeed.targetJobIds
        ));
        appendTimeRange(sql, args, "created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(seed.limit);

        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(row -> interviewSessionNode(row, seed))
                .toList();
    }

    private TraceNodeVO interviewSessionNode(Map<String, Object> row, TraceQuery seed) {
        Long id = asLong(row, "id");
        TraceNodeVO node = baseNode("interview-session-" + id, MOD_INTERVIEW_SESSION, "interview_session", id,
                firstText(asString(row, "title"), "Interview session " + id), normalizeStatus(asString(row, "status"), false),
                seed, null, null, String.valueOf(id), MOD_INTERVIEW_SESSION, String.valueOf(id), asLong(row, "userId"),
                firstDateTime(row, "startTime", "createdAt"), weakAssociation(seed, "Interview session has no persisted traceId; association is inferred from business keys."));
        node.setSummary(safeText(firstText(asString(row, "targetPosition"), asString(row, "mode")), 120));
        node.setPreview(maskText(asString(row, "failureReason"), 160));
        node.setContentHash(asString(row, "trainingContextHash"));
        node.setContentLength(asInteger(row, "trainingContextLength"));
        node.setRawAccess(rawAccess(asBoolean(row, "rawFieldsAvailable")));
        addPreview(node, "training context summary", null, asString(row, "trainingContextHash"), asInteger(row, "trainingContextLength"));
        node.getLinks().add(link("Interview session", "/admin/interviews", queryMap("interviewId", id)));
        putMeta(node, "applicationPackageId", asLong(row, "applicationPackageId"));
        putMeta(node, "applicationId", asLong(row, "applicationId"));
        putMeta(node, "resumeId", asLong(row, "resumeId"));
        putMeta(node, "resumeVersionId", asLong(row, "resumeVersionId"));
        putMeta(node, "targetJobId", asLong(row, "targetJobId"));
        putMeta(node, "jdAnalysisId", asLong(row, "jdAnalysisId"));
        putMeta(node, "skillProfileId", asLong(row, "skillProfileId"));
        putMeta(node, "matchReportId", asLong(row, "matchReportId"));
        putMeta(node, "mode", asString(row, "mode"));
        putMeta(node, "targetPosition", asString(row, "targetPosition"));
        putMeta(node, "trainingScene", asString(row, "trainingScene"));
        putMeta(node, "targetSkillDomain", asString(row, "targetSkillDomain"));
        putMeta(node, "reportStatus", asString(row, "reportStatus"));
        putMeta(node, "answeredQuestionCount", asLong(row, "answeredQuestionCount"));
        putMeta(node, "maxQuestionCount", asLong(row, "maxQuestionCount"));
        putMeta(node, "totalScore", asLong(row, "totalScore"));
        return node;
    }

    private List<TraceNodeVO> loadInterviewReportNodes(TraceQuery seed, Set<Long> sessionIds) {
        if (!seed.hasInterviewReportCriteria(sessionIds)) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, session_id AS sessionId, user_id AS userId, status, total_score AS totalScore,
                       summary, generated_at AS generatedAt, failure_reason AS failureReason,
                       created_at AS createdAt, updated_at AS updatedAt,
                       CASE WHEN qa_review IS NOT NULL OR report_content IS NOT NULL OR rubric_scores IS NOT NULL
                              OR follow_up_tree IS NOT NULL OR advice_evidence IS NOT NULL OR ability_profile_updates IS NOT NULL
                            THEN 1 ELSE 0 END AS rawFieldsAvailable,
                       CHAR_LENGTH(summary) AS summaryLength, SHA2(summary, 256) AS summaryHash,
                       CHAR_LENGTH(qa_review) AS qaReviewLength, SHA2(qa_review, 256) AS qaReviewHash,
                       CHAR_LENGTH(report_content) AS reportContentLength, SHA2(report_content, 256) AS reportContentHash,
                       CHAR_LENGTH(rubric_scores) AS rubricScoresLength, SHA2(rubric_scores, 256) AS rubricScoresHash,
                       CHAR_LENGTH(follow_up_tree) AS followUpTreeLength, SHA2(follow_up_tree, 256) AS followUpTreeHash,
                       CHAR_LENGTH(advice_evidence) AS adviceEvidenceLength, SHA2(advice_evidence, 256) AS adviceEvidenceHash,
                       CHAR_LENGTH(ability_profile_updates) AS abilityProfileUpdatesLength,
                       SHA2(ability_profile_updates, 256) AS abilityProfileUpdatesHash
                  FROM interview_report
                 WHERE deleted = 0
                """);
        appendEquals(sql, args, "user_id", seed.userId);
        if (seed.isBusinessType("INTERVIEW_REPORT") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "id", seed.businessIdAsLong());
        }
        if (seed.isBusinessType("INTERVIEW_SESSION", "INTERVIEW") && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "session_id", seed.businessIdAsLong());
        }
        appendIn(sql, args, "session_id", sessionIds);
        appendTimeRange(sql, args, "created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(seed.limit);

        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(row -> interviewReportNode(row, seed))
                .toList();
    }

    private TraceNodeVO interviewReportNode(Map<String, Object> row, TraceQuery seed) {
        Long id = asLong(row, "id");
        TraceNodeVO node = baseNode("interview-report-" + id, MOD_INTERVIEW_REPORT, "interview_report", id,
                "Interview report " + id, normalizeStatus(asString(row, "status"), false),
                seed, null, null, String.valueOf(id), MOD_INTERVIEW_REPORT, String.valueOf(id), asLong(row, "userId"),
                firstDateTime(row, "generatedAt", "createdAt"), weakAssociation(seed, "Interview report links through sessionId; it has no persisted traceId."));
        node.setSummary(maskText(asString(row, "summary"), 160));
        node.setPreview(maskText(firstText(asString(row, "failureReason"), asString(row, "summary")), 160));
        node.setContentHash(firstText(asString(row, "reportContentHash"), asString(row, "qaReviewHash"), asString(row, "summaryHash")));
        node.setContentLength(firstInt(row, "reportContentLength", "qaReviewLength", "summaryLength"));
        node.setRawAccess(rawAccess(asBoolean(row, "rawFieldsAvailable")));
        addPreview(node, "summary", null, asString(row, "summaryHash"), asInteger(row, "summaryLength"));
        addPreview(node, "qa review", null, asString(row, "qaReviewHash"), asInteger(row, "qaReviewLength"));
        addPreview(node, "report content", null, asString(row, "reportContentHash"), asInteger(row, "reportContentLength"));
        addPreview(node, "rubric scores", null, asString(row, "rubricScoresHash"), asInteger(row, "rubricScoresLength"));
        addPreview(node, "follow-up tree", null, asString(row, "followUpTreeHash"), asInteger(row, "followUpTreeLength"));
        addPreview(node, "advice evidence", null, asString(row, "adviceEvidenceHash"), asInteger(row, "adviceEvidenceLength"));
        addPreview(node, "ability updates", null, asString(row, "abilityProfileUpdatesHash"), asInteger(row, "abilityProfileUpdatesLength"));
        node.getLinks().add(link("Interview report", "/admin/interview-reports", queryMap("reportId", id, "sessionId", asLong(row, "sessionId"))));
        putMeta(node, "sessionId", asLong(row, "sessionId"));
        putMeta(node, "totalScore", asLong(row, "totalScore"));
        return node;
    }

    private List<TraceNodeVO> loadInterviewVoiceNodes(TraceQuery seed, Set<Long> sessionIds) {
        if (!seed.hasInterviewVoiceCriteria(sessionIds)) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT s.id AS submissionId, s.user_id AS userId, s.session_id AS sessionId,
                       s.question_message_id AS questionMessageId, s.question_id AS questionId,
                       s.file_id AS fileId, s.audio_duration_ms AS audioDurationMs, s.mime_type AS mimeType,
                       s.voice_status AS voiceStatus, s.trace_id AS traceId, s.fallback AS submissionFallback,
                       s.fallback_reason AS submissionFallbackReason, s.created_at AS submissionCreatedAt,
                       s.updated_at AS submissionUpdatedAt,
                       t.id AS transcriptId, t.confidence, t.transcript_status AS transcriptStatus,
                       t.asr_provider AS asrProvider, t.fallback AS transcriptFallback,
                       t.fallback_reason AS transcriptFallbackReason, t.confirmed_at AS confirmedAt,
                       t.submitted_answer_message_id AS submittedAnswerMessageId, t.submitted_at AS submittedAt,
                       CHAR_LENGTH(t.draft_text) AS draftTextLength, SHA2(t.draft_text, 256) AS draftTextHash,
                       CHAR_LENGTH(t.confirmed_text) AS confirmedTextLength, SHA2(t.confirmed_text, 256) AS confirmedTextHash
                  FROM interview_voice_submission s
                  LEFT JOIN interview_transcript t ON t.id = (
                        SELECT it.id
                          FROM interview_transcript it
                         WHERE it.voice_submission_id = s.id
                           AND it.deleted = 0
                         ORDER BY it.id DESC
                         LIMIT 1
                  )
                 WHERE s.deleted = 0
                """);
        appendEquals(sql, args, "s.trace_id", seed.traceId);
        appendEquals(sql, args, "s.user_id", seed.userId);
        if (seed.isBusinessType(MOD_INTERVIEW_VOICE)) {
            appendEquals(sql, args, "s.id", seed.businessIdAsLong());
        }
        if (seed.isBusinessType("INTERVIEW_TRANSCRIPT")) {
            appendEquals(sql, args, "t.id", seed.businessIdAsLong());
        }
        if (seed.isBusinessType("INTERVIEW_SESSION", "INTERVIEW")) {
            appendEquals(sql, args, "s.session_id", seed.businessIdAsLong());
        }
        appendIn(sql, args, "s.session_id", sessionIds);
        appendTimeRange(sql, args, "s.created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY s.created_at DESC LIMIT ?");
        args.add(seed.limit);

        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(row -> interviewVoiceNode(row, seed))
                .toList();
    }

    private TraceNodeVO interviewVoiceNode(Map<String, Object> row, TraceQuery seed) {
        Long submissionId = asLong(row, "submissionId");
        Long sessionId = asLong(row, "sessionId");
        boolean fallback = Boolean.TRUE.equals(asBoolean(row, "submissionFallback"))
                || Boolean.TRUE.equals(asBoolean(row, "transcriptFallback"));
        String status = normalizeInterviewVoiceStatus(asString(row, "voiceStatus"), asString(row, "transcriptStatus"), fallback);
        TraceNodeVO node = baseNode("interview-voice-" + submissionId, MOD_INTERVIEW_VOICE, "interview_voice_submission", submissionId,
                "Interview voice submission " + submissionId, status,
                seed, asString(row, "traceId"), null, sessionId == null ? null : String.valueOf(sessionId),
                MOD_INTERVIEW_VOICE, String.valueOf(submissionId), asLong(row, "userId"),
                firstDateTime(row, "submittedAt", "confirmedAt", "submissionCreatedAt"),
                association(seed, asString(row, "traceId"), null, null, null, sessionId == null ? null : String.valueOf(sessionId), submissionId));
        String fallbackReason = firstText(asString(row, "transcriptFallbackReason"), asString(row, "submissionFallbackReason"));
        node.setSummary(safeText(firstText(asString(row, "transcriptStatus"), asString(row, "voiceStatus")), 120));
        node.setPreview(maskText(fallbackReason, 160));
        node.setContentHash(firstText(asString(row, "confirmedTextHash"), asString(row, "draftTextHash")));
        node.setContentLength(firstInt(row, "confirmedTextLength", "draftTextLength"));
        node.setRawAccess(rawAccess(asInteger(row, "confirmedTextLength") != null || asInteger(row, "draftTextLength") != null));
        addPreview(node, "draft transcript", null, asString(row, "draftTextHash"), asInteger(row, "draftTextLength"));
        addPreview(node, "confirmed transcript", null, asString(row, "confirmedTextHash"), asInteger(row, "confirmedTextLength"));
        node.getLinks().add(link("Interview session", "/admin/interviews", queryMap("interviewId", sessionId)));
        putMeta(node, "sessionId", sessionId);
        putMeta(node, "questionMessageId", asLong(row, "questionMessageId"));
        putMeta(node, "questionId", asLong(row, "questionId"));
        putMeta(node, "fileId", asLong(row, "fileId"));
        putMeta(node, "audioDurationMs", asLong(row, "audioDurationMs"));
        putMeta(node, "mimeType", asString(row, "mimeType"));
        putMeta(node, "transcriptId", asLong(row, "transcriptId"));
        putMeta(node, "confidence", asBigDecimal(row, "confidence"));
        putMeta(node, "transcriptStatus", asString(row, "transcriptStatus"));
        putMeta(node, "asrProvider", asString(row, "asrProvider"));
        putMeta(node, "submittedAnswerMessageId", asLong(row, "submittedAnswerMessageId"));
        putMeta(node, "fallback", fallback);
        putMeta(node, "fallbackReason", maskText(fallbackReason, 120));
        return node;
    }

    private List<TraceNodeVO> loadUsageReferenceNodes(TraceQuery seed, Map<String, TraceNodeVO> nodesById) {
        List<Map<String, Object>> rows = loadUsageReferenceRows(seed, nodesById);
        if (rows.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, TraceNodeVO> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            TraceNodeVO source = usageSourceNode(row, seed);
            nodes.putIfAbsent(source.getId(), source);
            TraceNodeVO consumer = usageConsumerNode(row, seed);
            if (consumer != null && !nodesById.containsKey(consumer.getId())) {
                nodes.putIfAbsent(consumer.getId(), consumer);
            }
        }
        return new ArrayList<>(nodes.values());
    }

    private List<TraceEdgeVO> loadUsageReferenceEdges(TraceQuery seed, Map<String, TraceNodeVO> nodesById) {
        List<Map<String, Object>> rows;
        try {
            rows = loadUsageReferenceRows(seed, nodesById);
        } catch (DataAccessException ex) {
            log.warn("Trace Cockpit usage reference edge aggregation failed: {}", safeError(ex));
            return List.of();
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<TraceEdgeVO> edges = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String from = usageSourceNodeId(asString(row, "sourceType"), asLong(row, "sourceId"));
            String to = usageConsumerNodeId(asString(row, "consumerType"), asLong(row, "consumerId"));
            if (nodesById.containsKey(from) && nodesById.containsKey(to)) {
                addEdge(edges, seen, from, to, usageEdgeType(asString(row, "sourceType")),
                        firstText(asString(row, "usageScene"), "context usage"),
                        firstText(asString(row, "usageStrength"), "MEDIUM"),
                        "Usage reference table links this source to the consumer.");
            }
        }
        return edges;
    }

    private List<Map<String, Object>> loadUsageReferenceRows(TraceQuery seed, Map<String, TraceNodeVO> nodesById) {
        if (!seed.hasUsageReferenceCriteria(nodesById)) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, user_id AS userId, source_type AS sourceType, source_id AS sourceId,
                       source_version AS sourceVersion, consumer_type AS consumerType, consumer_id AS consumerId,
                       trace_id AS traceId, usage_scene AS usageScene, usage_strength AS usageStrength,
                       confidence, snapshot_hash AS snapshotHash, created_at AS createdAt, updated_at AS updatedAt
                  FROM agent_context_usage_reference
                 WHERE deleted = 0
                """);
        appendEquals(sql, args, "trace_id", seed.traceId);
        appendEquals(sql, args, "user_id", seed.userId);
        appendTimeRange(sql, args, "created_at", seed.startTime, seed.endTime);
        appendUsageBusinessCriteria(sql, args, seed);
        if (!seed.hasDirectUsageReferenceCriteria()) {
            appendUsageConsumerCriteria(sql, args, nodesById);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(seed.limit);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private void appendUsageBusinessCriteria(StringBuilder sql, List<Object> args, TraceQuery seed) {
        Long businessId = seed.businessIdAsLong();
        if (businessId == null) {
            return;
        }
        if (seed.isBusinessType(MOD_KNOWLEDGE_DOCUMENT, "PERSONAL_KNOWLEDGE_DOCUMENT")) {
            appendEquals(sql, args, "source_type", MOD_KNOWLEDGE_DOCUMENT);
            appendEquals(sql, args, "source_id", businessId);
        } else if (seed.isBusinessType(MOD_KNOWLEDGE_CHUNK, "PERSONAL_KNOWLEDGE_CHUNK")) {
            appendEquals(sql, args, "source_type", MOD_KNOWLEDGE_CHUNK);
            appendEquals(sql, args, "source_id", businessId);
        } else if (seed.isBusinessType(MOD_MEMORY, "AGENT_MEMORY")) {
            appendEquals(sql, args, "source_type", MOD_MEMORY);
            appendEquals(sql, args, "source_id", businessId);
        } else if (seed.isBusinessType(MOD_AI_CALL, MOD_AGENT_RUN, MOD_AGENT_TASK,
                MOD_APPLICATION_PACKAGE, MOD_INTERVIEW_REPORT)) {
            appendEquals(sql, args, "consumer_type", seed.businessType);
            appendEquals(sql, args, "consumer_id", businessId);
        }
    }

    private void appendUsageConsumerCriteria(StringBuilder sql, List<Object> args, Map<String, TraceNodeVO> nodesById) {
        Map<String, Set<Long>> consumerIds = usageConsumerIds(nodesById.values());
        if (consumerIds.isEmpty()) {
            return;
        }
        List<String> clauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        consumerIds.forEach((consumerType, ids) -> {
            if (ids == null || ids.isEmpty()) {
                return;
            }
            clauses.add("(consumer_type = ? AND consumer_id IN ("
                    + ids.stream().map(id -> "?").collect(Collectors.joining(",")) + "))");
            values.add(consumerType);
            values.addAll(ids);
        });
        if (clauses.isEmpty()) {
            return;
        }
        sql.append(" AND (").append(String.join(" OR ", clauses)).append(")");
        args.addAll(values);
    }

    private Map<String, Set<Long>> usageConsumerIds(Collection<TraceNodeVO> nodes) {
        Map<String, Set<Long>> ids = new LinkedHashMap<>();
        for (TraceNodeVO node : nodes) {
            Long sourceId = parseLong(node.getSourceId());
            if (sourceId == null || !isUsageConsumerType(node.getNodeType())) {
                continue;
            }
            ids.computeIfAbsent(node.getNodeType(), ignored -> new LinkedHashSet<>()).add(sourceId);
        }
        return ids;
    }

    private TraceNodeVO usageSourceNode(Map<String, Object> row, TraceQuery seed) {
        String sourceType = asString(row, "sourceType");
        Long sourceId = asLong(row, "sourceId");
        String nodeType = usageSourceNodeType(sourceType);
        TraceNodeVO node = baseNode(usageSourceNodeId(sourceType, sourceId), nodeType,
                "agent_context_usage_reference", sourceId, usageSourceTitle(sourceType, sourceId), "SUCCESS",
                seed, asString(row, "traceId"), null, null, sourceType, String.valueOf(sourceId),
                asLong(row, "userId"), asDateTime(row, "createdAt"),
                association(seed, asString(row, "traceId"), null, null, sourceType, String.valueOf(sourceId), sourceId));
        node.setSummary("Context source referenced by " + asString(row, "consumerType") + "#" + asLong(row, "consumerId"));
        node.setContentHash(asString(row, "snapshotHash"));
        node.setRawAccess(rawAccess(false));
        addPreview(node, "usage snapshot", null, asString(row, "snapshotHash"), null);
        putMeta(node, "sourceType", sourceType);
        putMeta(node, "sourceId", sourceId);
        putMeta(node, "sourceVersion", asString(row, "sourceVersion"));
        putMeta(node, "usageScene", asString(row, "usageScene"));
        putMeta(node, "usageStrength", asString(row, "usageStrength"));
        putMeta(node, "confidence", asBigDecimal(row, "confidence"));
        putMeta(node, "consumerType", asString(row, "consumerType"));
        putMeta(node, "consumerId", asLong(row, "consumerId"));
        return node;
    }

    private TraceNodeVO usageConsumerNode(Map<String, Object> row, TraceQuery seed) {
        String consumerType = asString(row, "consumerType");
        Long consumerId = asLong(row, "consumerId");
        if (consumerId == null || !isUsageConsumerType(consumerType)) {
            return null;
        }
        TraceNodeVO node = baseNode(usageConsumerNodeId(consumerType, consumerId), consumerType,
                "agent_context_usage_reference_consumer", consumerId, consumerType + " " + consumerId, "UNKNOWN",
                seed, asString(row, "traceId"), null, null, consumerType, String.valueOf(consumerId),
                asLong(row, "userId"), asDateTime(row, "createdAt"),
                association(seed, asString(row, "traceId"), null, null, consumerType, String.valueOf(consumerId), consumerId));
        node.setSummary("Consumer inferred from usage reference metadata.");
        node.setRawAccess(rawAccess(false));
        putMeta(node, "usageReferenceOnly", true);
        return node;
    }

    private String usageSourceNodeType(String sourceType) {
        if (MOD_KNOWLEDGE_DOCUMENT.equals(sourceType)) {
            return MOD_KNOWLEDGE_DOCUMENT;
        }
        if (MOD_KNOWLEDGE_CHUNK.equals(sourceType)) {
            return MOD_KNOWLEDGE_CHUNK;
        }
        if (MOD_MEMORY.equals(sourceType)) {
            return MOD_MEMORY;
        }
        return MOD_CONTEXT_USAGE_REFERENCE;
    }

    private String usageSourceNodeId(String sourceType, Long sourceId) {
        if (MOD_KNOWLEDGE_DOCUMENT.equals(sourceType)) {
            return "knowledge-document-" + sourceId;
        }
        if (MOD_KNOWLEDGE_CHUNK.equals(sourceType)) {
            return "knowledge-chunk-" + sourceId;
        }
        if (MOD_MEMORY.equals(sourceType)) {
            return "memory-" + sourceId;
        }
        return "context-source-" + safeNodePart(sourceType) + "-" + sourceId;
    }

    private String usageConsumerNodeId(String consumerType, Long consumerId) {
        if (MOD_AI_CALL.equals(consumerType)) {
            return "ai-" + consumerId;
        }
        if (MOD_AGENT_RUN.equals(consumerType)) {
            return "agent-run-" + consumerId;
        }
        if (MOD_AGENT_TASK.equals(consumerType)) {
            return "agent-task-" + consumerId;
        }
        if (MOD_AGENT_WEEK_PLAN.equals(consumerType)) {
            return "agent-week-plan-" + consumerId;
        }
        if (MOD_AGENT_WEEK_PLAN_ITEM.equals(consumerType)) {
            return "agent-week-plan-item-" + consumerId;
        }
        if (MOD_APPLICATION_PACKAGE.equals(consumerType)) {
            return "application-package-" + consumerId;
        }
        if (MOD_INTERVIEW_REPORT.equals(consumerType)) {
            return "interview-report-" + consumerId;
        }
        return "context-consumer-" + safeNodePart(consumerType) + "-" + consumerId;
    }

    private String usageSourceTitle(String sourceType, Long sourceId) {
        if (MOD_KNOWLEDGE_DOCUMENT.equals(sourceType)) {
            return "Knowledge document " + sourceId;
        }
        if (MOD_KNOWLEDGE_CHUNK.equals(sourceType)) {
            return "Knowledge chunk " + sourceId;
        }
        if (MOD_MEMORY.equals(sourceType)) {
            return "Agent memory " + sourceId;
        }
        return firstText(sourceType, "Context source") + " " + sourceId;
    }

    private String usageEdgeType(String sourceType) {
        if (MOD_MEMORY.equals(sourceType)) {
            return "REFERENCED_MEMORY";
        }
        if (MOD_KNOWLEDGE_DOCUMENT.equals(sourceType) || MOD_KNOWLEDGE_CHUNK.equals(sourceType)) {
            return "REFERENCED_KNOWLEDGE";
        }
        return "REFERENCED_CONTEXT";
    }

    private boolean isUsageConsumerType(String consumerType) {
        return MOD_AI_CALL.equals(consumerType)
                || MOD_AGENT_RUN.equals(consumerType)
                || MOD_AGENT_TASK.equals(consumerType)
                || MOD_AGENT_WEEK_PLAN.equals(consumerType)
                || MOD_AGENT_WEEK_PLAN_ITEM.equals(consumerType)
                || MOD_APPLICATION_PACKAGE.equals(consumerType)
                || MOD_INTERVIEW_REPORT.equals(consumerType);
    }

    private String safeNodePart(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }

    private List<TraceEdgeVO> loadApplicationPackageEventEdges(TraceQuery seed, Map<String, TraceNodeVO> nodesById) {
        Set<Long> packageIds = sourceIds(nodesById.values(), MOD_APPLICATION_PACKAGE);
        if (packageIds.isEmpty() && !StringUtils.hasText(seed.traceId)) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT package_id AS packageId, event_type AS eventType, action_code AS actionCode,
                       related_biz_type AS relatedBizType, related_biz_id AS relatedBizId,
                       trace_id AS traceId, snapshot_version AS snapshotVersion, event_time AS eventTime
                  FROM job_application_package_event
                 WHERE deleted = 0
                """);
        if (!packageIds.isEmpty()) {
            appendIn(sql, args, "package_id", packageIds);
        }
        appendEquals(sql, args, "trace_id", seed.traceId);
        sql.append(" ORDER BY event_time DESC LIMIT ?");
        args.add(seed.limit);

        Set<String> seen = new LinkedHashSet<>();
        List<TraceEdgeVO> edges = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), args.toArray())) {
            String from = "application-package-" + asLong(row, "packageId");
            String to = findBusinessNode(nodesById.values(), asString(row, "relatedBizType"), asString(row, "relatedBizId"));
            if (nodesById.containsKey(from) && StringUtils.hasText(to)) {
                addEdge(edges, seen, from, to, "PACKAGE_EVENT",
                        firstText(asString(row, "eventType"), asString(row, "actionCode"), "package event"),
                        "MEDIUM", "Application package event references this related business object.");
            }
        }
        return edges;
    }

    private List<TraceEdgeVO> loadWeekPlanInfluenceEdges(TraceQuery seed, Map<String, TraceNodeVO> nodesById) {
        Set<Long> weekPlanIds = sourceIds(nodesById.values(), MOD_AGENT_WEEK_PLAN);
        Set<Long> itemIds = sourceIds(nodesById.values(), MOD_AGENT_WEEK_PLAN_ITEM);
        if (!seed.hasWeekPlanInfluenceCriteria(weekPlanIds, itemIds)) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT week_plan_id AS weekPlanId, week_plan_item_id AS weekPlanItemId,
                       source_type AS sourceType, source_id AS sourceId,
                       usage_reference_id AS usageReferenceId, influence_strength AS influenceStrength,
                       confidence, trace_id AS traceId, snapshot_version AS snapshotVersion,
                       created_at AS createdAt
                  FROM agent_plan_influence
                 WHERE deleted = 0
                """);
        appendIn(sql, args, "week_plan_id", weekPlanIds);
        appendIn(sql, args, "week_plan_item_id", itemIds);
        appendEquals(sql, args, "trace_id", seed.traceId);
        appendEquals(sql, args, "user_id", seed.userId);
        if (seed.isBusinessType(MOD_AGENT_WEEK_PLAN) && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "week_plan_id", seed.businessIdAsLong());
        } else if (seed.isBusinessType(MOD_AGENT_WEEK_PLAN_ITEM) && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "week_plan_item_id", seed.businessIdAsLong());
        } else if (StringUtils.hasText(seed.businessType) && seed.businessIdAsLong() != null) {
            appendEquals(sql, args, "source_type", seed.businessType);
            appendEquals(sql, args, "source_id", seed.businessIdAsLong());
        }
        appendTimeRange(sql, args, "created_at", seed.startTime, seed.endTime);
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(seed.limit);

        Set<String> seen = new LinkedHashSet<>();
        List<TraceEdgeVO> edges = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), args.toArray())) {
            String to = null;
            Long itemId = asLong(row, "weekPlanItemId");
            Long planId = asLong(row, "weekPlanId");
            if (itemId != null && nodesById.containsKey("agent-week-plan-item-" + itemId)) {
                to = "agent-week-plan-item-" + itemId;
            } else if (planId != null && nodesById.containsKey("agent-week-plan-" + planId)) {
                to = "agent-week-plan-" + planId;
            }
            String from = influenceSourceNodeId(nodesById.values(), asString(row, "sourceType"), asLong(row, "sourceId"));
            if (nodesById.containsKey(from) && nodesById.containsKey(to)) {
                addEdge(edges, seen, from, to, "WEEK_PLAN_INFLUENCE",
                        firstText(asString(row, "sourceType"), "influence"),
                        firstText(asString(row, "influenceStrength"), "MEDIUM"),
                        "Agent week plan influence table links this source to the plan item.");
            }
        }
        return edges;
    }

    private List<TraceEdgeVO> buildEdges(Collection<TraceNodeVO> nodes) {
        List<TraceEdgeVO> edges = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Map<String, TraceNodeVO> byId = nodes.stream().collect(Collectors.toMap(TraceNodeVO::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        for (TraceNodeVO node : nodes) {
            if (MOD_AGENT_RUN.equals(node.getNodeType())) {
                Long aiCallLogId = metaLong(node, "aiCallLogId");
                if (aiCallLogId != null && byId.containsKey("ai-" + aiCallLogId)) {
                    addEdge(edges, seen, node.getId(), "ai-" + aiCallLogId, "AI_CALL_LOG", "AI call", "HIGH",
                            "Agent run records aiCallLogId.");
                }
            }
            if (MOD_AGENT_TASK.equals(node.getNodeType())) {
                Long agentRunId = metaLong(node, "agentRunId");
                if (agentRunId != null && byId.containsKey("agent-run-" + agentRunId)) {
                    addEdge(edges, seen, "agent-run-" + agentRunId, node.getId(), "AGENT_TASK", "generated task", "MEDIUM",
                            "Agent task records agentRunId; this is a structural link, not proof of the same traceId.");
                }
            }
            if (MOD_AGENT_WEEK_PLAN.equals(node.getNodeType())) {
                Long agentRunId = metaLong(node, "agentRunId");
                if (agentRunId != null && byId.containsKey("agent-run-" + agentRunId)) {
                    addEdge(edges, seen, "agent-run-" + agentRunId, node.getId(), "WEEK_PLAN_SOURCE", "week plan source", "MEDIUM",
                            "Agent week plan records the primary agentRunId used for its snapshot.");
                }
            }
            if (MOD_AGENT_WEEK_PLAN_ITEM.equals(node.getNodeType())) {
                Long weekPlanId = metaLong(node, "weekPlanId");
                if (weekPlanId != null && byId.containsKey("agent-week-plan-" + weekPlanId)) {
                    addEdge(edges, seen, "agent-week-plan-" + weekPlanId, node.getId(), "WEEK_PLAN_ITEM", "plan item", "HIGH",
                            "Agent week plan item records weekPlanId.");
                }
                Long agentTaskId = metaLong(node, "agentTaskId");
                if (agentTaskId != null && byId.containsKey("agent-task-" + agentTaskId)) {
                    addEdge(edges, seen, "agent-task-" + agentTaskId, node.getId(), "TASK_TO_WEEK_PLAN_ITEM", "planned from task", "HIGH",
                            "Agent week plan item records agentTaskId.");
                }
            }
            if (MOD_INTERVIEW_REPORT.equals(node.getNodeType())) {
                Long sessionId = metaLong(node, "sessionId");
                if (sessionId != null && byId.containsKey("interview-session-" + sessionId)) {
                    addEdge(edges, seen, "interview-session-" + sessionId, node.getId(), "INTERVIEW_REPORT", "report", "HIGH",
                            "Interview report records sessionId.");
                }
            }
            if (MOD_INTERVIEW_VOICE.equals(node.getNodeType())) {
                Long sessionId = metaLong(node, "sessionId");
                if (sessionId != null && byId.containsKey("interview-session-" + sessionId)) {
                    addEdge(edges, seen, "interview-session-" + sessionId, node.getId(), "INTERVIEW_VOICE", "voice answer", "HIGH",
                            "Interview voice submission records sessionId.");
                }
            }
        }
        List<TraceNodeVO> packages = nodes.stream().filter(item -> MOD_APPLICATION_PACKAGE.equals(item.getNodeType())).toList();
        List<TraceNodeVO> sessions = nodes.stream().filter(item -> MOD_INTERVIEW_SESSION.equals(item.getNodeType())).toList();
        for (TraceNodeVO pkg : packages) {
            for (TraceNodeVO session : sessions) {
                if (sameMeta(pkg, session, "applicationPackageId")) {
                    addEdge(edges, seen, pkg.getId(), session.getId(), "PACKAGE_INTERVIEW", "interview", "HIGH",
                            "Interview session records applicationPackageId.");
                } else if (sameMeta(pkg, session, "applicationId")) {
                    addEdge(edges, seen, pkg.getId(), session.getId(), "APPLICATION_INTERVIEW", "interview", "MEDIUM",
                            "Application package and interview session share applicationId.");
                } else if (sameMeta(pkg, session, "matchReportId") || sameMeta(pkg, session, "targetJobId")) {
                    addEdge(edges, seen, pkg.getId(), session.getId(), "WEAK_BUSINESS_LINK", "related interview", "LOW",
                            "Association is inferred from matchReportId or targetJobId.");
                }
            }
        }
        for (TraceNodeVO async : nodes.stream().filter(item -> MOD_ASYNC_TASK.equals(item.getNodeType())).toList()) {
            String target = findBusinessNode(nodes, async.getBizType(), async.getBizId());
            if (StringUtils.hasText(target)) {
                addEdge(edges, seen, async.getId(), target, "ASYNC_BIZ", "business target", "MEDIUM",
                        "Async task bizType/bizId references this node.");
            }
        }
        return edges;
    }

    private TraceCockpitResultVO buildResult(TraceQuery seed, LinkedHashMap<String, TraceNodeVO> nodesById,
                                             List<TraceEdgeVO> edges, List<TraceModuleStatusVO> statuses) {
        List<TraceNodeVO> sortedNodes = nodesById.values().stream()
                .sorted(Comparator.comparing(TraceNodeVO::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TraceNodeVO::getId))
                .toList();
        TraceCockpitResultVO result = new TraceCockpitResultVO();
        result.setSource(SOURCE_BACKEND);
        result.setDataSource(SOURCE_BACKEND);
        result.setNodes(sortedNodes);
        result.setEdges(edges);
        result.setModuleStatuses(statuses);
        result.setRisks(buildRisks(sortedNodes, statuses));
        result.setSuggestions(buildSuggestions(sortedNodes));
        result.setOverview(buildOverview(seed, sortedNodes, statuses));
        result.setPartialResult(result.getOverview().getPartialResult());
        TraceTimelineVO timeline = new TraceTimelineVO();
        timeline.setNodes(sortedNodes);
        timeline.setUnplacedNodes(List.of());
        result.setTimeline(timeline);
        return result;
    }

    private TraceOverviewVO buildOverview(TraceQuery seed, List<TraceNodeVO> nodes, List<TraceModuleStatusVO> statuses) {
        TraceOverviewVO overview = new TraceOverviewVO();
        overview.setQueryId(firstText(seed.traceId, seed.requestId, seed.businessId, seed.messageId, seed.keyword, ""));
        overview.setResolvedLookupType(firstText(seed.lookupType, "auto"));
        List<String> traceIds = nodes.stream().map(TraceNodeVO::getTraceId).filter(StringUtils::hasText).distinct().toList();
        overview.setTraceIds(traceIds);
        overview.setPrimaryTraceId(traceIds.isEmpty() ? null : traceIds.get(0));
        overview.setSampleCount(nodes.size());
        overview.setFirstSeenAt(nodes.stream().map(TraceNodeVO::getOccurredAt).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null));
        overview.setLastSeenAt(nodes.stream().map(TraceNodeVO::getOccurredAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null));
        overview.setQueryStartTime(seed.startTime);
        overview.setQueryEndTime(seed.endTime);
        overview.setDefaultTimeWindowApplied(seed.defaultTimeWindowApplied);
        overview.setQueryLimit(seed.limit);
        overview.setMaxLimit(MAX_LIMIT);
        overview.setLowConfidenceCount((int) nodes.stream().filter(item -> "LOW".equals(item.getAssociationConfidence())).count());
        overview.setAiCallCount(countFor(statuses, MOD_AI_CALL));
        overview.setAgentRunCount(countFor(statuses, MOD_AGENT_RUN));
        overview.setAgentTaskCount(countFor(statuses, MOD_AGENT_TASK));
        overview.setAgentWeekPlanCount(countFor(statuses, MOD_AGENT_WEEK_PLAN));
        overview.setAgentWeekPlanItemCount(countFor(statuses, MOD_AGENT_WEEK_PLAN_ITEM));
        overview.setAsyncTaskCount(countFor(statuses, MOD_ASYNC_TASK));
        overview.setApplicationPackageCount(countFor(statuses, MOD_APPLICATION_PACKAGE));
        overview.setInterviewSessionCount(countFor(statuses, MOD_INTERVIEW_SESSION));
        overview.setInterviewReportCount(countFor(statuses, MOD_INTERVIEW_REPORT));
        overview.setInterviewVoiceCount(countFor(statuses, MOD_INTERVIEW_VOICE));
        overview.setFailedCount((int) nodes.stream().filter(item -> "FAILED".equals(item.getStatus())).count());
        overview.setFallbackCount((int) nodes.stream().filter(item -> "FALLBACK".equals(item.getStatus()) || Boolean.TRUE.equals(item.getMeta().get("fallback"))).count());
        overview.setTotalTokens(nodes.stream().map(item -> metaLong(item, "totalTokens")).filter(Objects::nonNull).reduce(0L, Long::sum));
        overview.setMaxElapsedMs(nodes.stream()
                .map(item -> (Long) firstNonNull(metaLong(item, "elapsedMs"), metaLong(item, "durationMs")))
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(null));
        overview.setRawFieldsAvailable(nodes.stream().anyMatch(item -> item.getRawAccess() != null && Boolean.TRUE.equals(item.getRawAccess().getRawFieldsAvailable())));
        overview.setRawFieldsIncluded(false);
        overview.setRawAccessPermission(RAW_PERMISSION);
        boolean partial = statuses.stream().anyMatch(item -> "FAILED".equals(item.getStatus()));
        overview.setPartialResult(partial);
        overview.setHealthStatus(partial ? "PARTIAL" : overview.getFailedCount() > 0 ? "FAILED" : nodes.isEmpty() ? "UNKNOWN" : "SUCCESS");
        overview.setModuleStatuses(statuses);
        return overview;
    }

    private List<TraceRiskVO> buildRisks(List<TraceNodeVO> nodes, List<TraceModuleStatusVO> statuses) {
        List<TraceRiskVO> risks = new ArrayList<>();
        if (statuses.stream().anyMatch(item -> "FAILED".equals(item.getStatus()))) {
            TraceRiskVO risk = risk("partial-result", "PARTIAL_RESULT", "MEDIUM", "Partial result",
                    "At least one source module failed. Counts from that module are unavailable, not zero.", null);
            risks.add(risk);
        }
        for (TraceNodeVO node : nodes) {
            if ("LOW".equals(node.getAssociationConfidence())) {
                TraceRiskVO risk = risk("weak-" + node.getId(), "WEAK_ASSOCIATION", "LOW", "Weak association",
                        node.getAssociationReason(), node.getId());
                risk.setLink(firstLink(node));
                risks.add(risk);
            }
            if ("FAILED".equals(node.getStatus())) {
                TraceRiskVO risk = risk("failed-" + node.getId(), failureRiskType(node), "MEDIUM", "Failed node",
                        "The source record is marked failed; inspect its source page before remediation.", node.getId());
                risk.setLink(firstLink(node));
                risks.add(risk);
            }
            if ("FALLBACK".equals(node.getStatus()) || Boolean.TRUE.equals(node.getMeta().get("fallback"))) {
                TraceRiskVO risk = risk("fallback-" + node.getId(), "FALLBACK", "LOW", "Fallback observed",
                        "The node appears degraded or fallback-sourced. Verify route and source module context.", node.getId());
                risk.setLink(firstLink(node));
                risks.add(risk);
            }
        }
        nodes.stream()
                .filter(item -> item.getRawAccess() != null && Boolean.TRUE.equals(item.getRawAccess().getRawFieldsAvailable()))
                .findFirst()
                .ifPresent(node -> {
                    TraceRiskVO risk = risk("raw-available", "RAW_AVAILABLE", "INFO", "Sensitive source recorded",
                            "Raw source material exists in at least one module. Trace Cockpit preview shows hash and length only; raw text is not returned here.", node.getId());
                    risk.setLink(firstLink(node));
                    risks.add(risk);
                });
        return risks;
    }

    private List<TraceGovernanceSuggestionVO> buildSuggestions(List<TraceNodeVO> nodes) {
        List<TraceGovernanceSuggestionVO> suggestions = new ArrayList<>();
        nodes.stream().filter(item -> "FAILED".equals(item.getStatus())).findFirst().ifPresent(node -> {
            TraceGovernanceSuggestionVO suggestion = suggestion("view-failed-source", actionFor(node), "Inspect failed source record",
                    "A failed node is present. Open the source module to inspect existing diagnostics before taking action.", "MEDIUM", node);
            suggestions.add(suggestion);
        });
        nodes.stream().filter(item -> "FALLBACK".equals(item.getStatus()) || Boolean.TRUE.equals(item.getMeta().get("fallback"))).findFirst().ifPresent(node -> {
            TraceGovernanceSuggestionVO suggestion = suggestion("check-fallback-route", "CHECK_MODEL_ROUTE", "Check route and fallback context",
                    "Fallback appeared in this sample. Trace Cockpit does not change routes; use source admin pages for review.", "LOW", node);
            suggestions.add(suggestion);
        });
        nodes.stream().filter(item -> item.getRawAccess() != null && Boolean.TRUE.equals(item.getRawAccess().getRawFieldsAvailable())).findFirst().ifPresent(node -> {
            TraceGovernanceSuggestionVO suggestion = suggestion("review-sensitive-access", "REVIEW_RAW_PERMISSION", "Review sensitive access boundary",
                    "Sensitive source material is recorded. Raw access remains in dedicated POST endpoints with confirmation.", "INFO", node);
            suggestion.setRequiredPermission(RAW_PERMISSION);
            suggestions.add(suggestion);
        });
        return suggestions;
    }

    private TraceNodeVO baseNode(String id, String nodeType, String sourceType, Long sourceId, String title, String status,
                                 TraceQuery seed, String traceId, String requestId, String businessId,
                                 String bizType, String bizId, Long userId, LocalDateTime occurredAt, Association association) {
        TraceNodeVO node = new TraceNodeVO();
        node.setId(id);
        node.setNodeType(nodeType);
        node.setSourceModule(nodeType);
        node.setSourceType(sourceType);
        node.setSourceId(sourceId == null ? null : String.valueOf(sourceId));
        node.setTitle(maskText(title, 120));
        node.setStatus(status);
        node.setTraceId(traceId);
        node.setRequestId(requestId);
        node.setBusinessId(businessId);
        node.setBusinessType(firstText(seed.businessType, bizType));
        node.setBizType(bizType);
        node.setBizId(bizId);
        node.setUserId(userId);
        node.setOccurredAt(occurredAt);
        node.setAssociationType(association.type);
        node.setAssociationConfidence(association.confidence);
        node.setAssociationReason(association.reason);
        node.setRawAccess(rawAccess(false));
        return node;
    }

    private Association association(TraceQuery seed, String traceId, String requestId, String messageId,
                                    String bizType, String bizId, Long sourceId) {
        if (StringUtils.hasText(seed.traceId) && seed.traceId.equals(traceId)) {
            return new Association("EXACT_TRACE", "HIGH", "The node carries the same traceId as the query.");
        }
        if (StringUtils.hasText(seed.requestId) && seed.requestId.equals(requestId)) {
            return new Association("EXACT_REQUEST", "HIGH", "The node matches the requestId from the query.");
        }
        if (StringUtils.hasText(seed.messageId) && seed.messageId.equals(messageId)) {
            return new Association("SAME_MESSAGE", "MEDIUM", "The node is linked by messageId.");
        }
        if (StringUtils.hasText(seed.bizType) && seed.bizType.equals(bizType) && Objects.equals(seed.bizId, bizId)) {
            return new Association("SAME_BIZ", "MEDIUM", "The node is linked by bizType and bizId.");
        }
        if (seed.agentRunId != null && Objects.equals(seed.agentRunId, sourceId)) {
            return new Association("SAME_AGENT_RUN", "HIGH", "The node matches the requested Agent Run id.");
        }
        if (StringUtils.hasText(seed.businessId)) {
            return new Association("SAME_BIZ", "MEDIUM", "The node is associated by businessId; verify source module context.");
        }
        if (seed.startTime != null || seed.endTime != null) {
            return new Association("TIME_WINDOW", "LOW", "The node is associated through the query time window.");
        }
        return new Association("MODULE_SEED", "LOW", "The node came from a module-level lookup and is not a confirmed trace edge.");
    }

    private Association weakAssociation(TraceQuery seed, String reason) {
        if (StringUtils.hasText(seed.businessId) || StringUtils.hasText(seed.bizId)) {
            return new Association("SAME_BIZ", "LOW", reason);
        }
        if (seed.userId != null || seed.startTime != null || seed.endTime != null) {
            return new Association("TIME_WINDOW", "LOW", reason);
        }
        return new Association("MODULE_SEED", "LOW", reason);
    }

    private void addEdge(List<TraceEdgeVO> edges, Set<String> seen, String from, String to, String edgeType,
                         String label, String confidence, String reason) {
        if (!StringUtils.hasText(from) || !StringUtils.hasText(to) || from.equals(to)) {
            return;
        }
        String key = from + "->" + to + ":" + edgeType;
        if (!seen.add(key)) {
            return;
        }
        TraceEdgeVO edge = new TraceEdgeVO();
        edge.setId("edge-" + edges.size() + "-" + Math.abs(key.hashCode()));
        edge.setFromNodeId(from);
        edge.setToNodeId(to);
        edge.setEdgeType(edgeType);
        edge.setLabel(label);
        edge.setConfidence(confidence);
        edge.setReason(reason);
        edges.add(edge);
    }

    private String findBusinessNode(Collection<TraceNodeVO> nodes, String bizType, String bizId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return null;
        }
        for (TraceNodeVO node : nodes) {
            if (bizType.equalsIgnoreCase(String.valueOf(node.getBizType())) && bizId.equals(String.valueOf(node.getBizId()))) {
                return node.getId();
            }
            if (MOD_APPLICATION_PACKAGE.equalsIgnoreCase(bizType) || "JOB_APPLICATION_PACKAGE".equalsIgnoreCase(bizType)) {
                if (("application-package-" + bizId).equals(node.getId())) {
                    return node.getId();
                }
            }
            if ("INTERVIEW_SESSION".equalsIgnoreCase(bizType) || "INTERVIEW".equalsIgnoreCase(bizType)) {
                if (("interview-session-" + bizId).equals(node.getId())) {
                    return node.getId();
                }
            }
            if ("INTERVIEW_REPORT".equalsIgnoreCase(bizType) && ("interview-report-" + bizId).equals(node.getId())) {
                return node.getId();
            }
            if (MOD_INTERVIEW_VOICE.equalsIgnoreCase(bizType) && ("interview-voice-" + bizId).equals(node.getId())) {
                return node.getId();
            }
            if ("INTERVIEW_TRANSCRIPT".equalsIgnoreCase(bizType)
                    && Objects.equals(String.valueOf(node.getMeta().get("transcriptId")), bizId)) {
                return node.getId();
            }
            if ("AGENT_RUN".equalsIgnoreCase(bizType) && ("agent-run-" + bizId).equals(node.getId())) {
                return node.getId();
            }
            if ("AGENT_TASK".equalsIgnoreCase(bizType) && ("agent-task-" + bizId).equals(node.getId())) {
                return node.getId();
            }
            if ("AI_CALL".equalsIgnoreCase(bizType) && ("ai-" + bizId).equals(node.getId())) {
                return node.getId();
            }
            if ("AGENT_WEEK_PLAN".equalsIgnoreCase(bizType) && ("agent-week-plan-" + bizId).equals(node.getId())) {
                return node.getId();
            }
            if ("AGENT_WEEK_PLAN_ITEM".equalsIgnoreCase(bizType) && ("agent-week-plan-item-" + bizId).equals(node.getId())) {
                return node.getId();
            }
            if (("KNOWLEDGE_DOCUMENT".equalsIgnoreCase(bizType) || "PERSONAL_KNOWLEDGE_DOCUMENT".equalsIgnoreCase(bizType))
                    && ("knowledge-document-" + bizId).equals(node.getId())) {
                return node.getId();
            }
            if (("KNOWLEDGE_CHUNK".equalsIgnoreCase(bizType) || "PERSONAL_KNOWLEDGE_CHUNK".equalsIgnoreCase(bizType))
                    && ("knowledge-chunk-" + bizId).equals(node.getId())) {
                return node.getId();
            }
            if (("MEMORY".equalsIgnoreCase(bizType) || "AGENT_MEMORY".equalsIgnoreCase(bizType))
                    && ("memory-" + bizId).equals(node.getId())) {
                return node.getId();
            }
        }
        return null;
    }

    private String influenceSourceNodeId(Collection<TraceNodeVO> nodes, String sourceType, Long sourceId) {
        if (!StringUtils.hasText(sourceType) || sourceId == null) {
            return null;
        }
        String sourceIdText = String.valueOf(sourceId);
        String businessNodeId = findBusinessNode(nodes, sourceType, sourceIdText);
        if (StringUtils.hasText(businessNodeId)) {
            return businessNodeId;
        }
        if (MOD_KNOWLEDGE_DOCUMENT.equals(sourceType) || "PERSONAL_KNOWLEDGE_DOCUMENT".equals(sourceType)) {
            return "knowledge-document-" + sourceId;
        }
        if (MOD_KNOWLEDGE_CHUNK.equals(sourceType) || "PERSONAL_KNOWLEDGE_CHUNK".equals(sourceType)) {
            return "knowledge-chunk-" + sourceId;
        }
        if (MOD_MEMORY.equals(sourceType) || "AGENT_MEMORY".equals(sourceType)) {
            return "memory-" + sourceId;
        }
        if (MOD_AGENT_TASK.equals(sourceType)) {
            return "agent-task-" + sourceId;
        }
        if (MOD_AGENT_RUN.equals(sourceType)) {
            return "agent-run-" + sourceId;
        }
        if (MOD_APPLICATION_PACKAGE.equals(sourceType) || "JOB_APPLICATION_PACKAGE".equals(sourceType)) {
            return "application-package-" + sourceId;
        }
        if (MOD_INTERVIEW_REPORT.equals(sourceType)) {
            return "interview-report-" + sourceId;
        }
        return null;
    }

    private TraceRawAccessStatusVO rawAccess(boolean rawFieldsAvailable) {
        TraceRawAccessStatusVO rawAccess = new TraceRawAccessStatusVO();
        rawAccess.setRawFieldsAvailable(rawFieldsAvailable);
        rawAccess.setRawFieldsIncluded(false);
        rawAccess.setState(rawFieldsAvailable ? "RECORDED_CAN_REQUEST" : "NOT_RECORDED");
        rawAccess.setRawAccessPermission(RAW_PERMISSION);
        rawAccess.setRequiredPermission(RAW_PERMISSION);
        rawAccess.setDisplayPolicy("HASH_LENGTH_ONLY");
        rawAccess.setNote("Trace Cockpit preview never includes raw source text; only hash, length, and permission hints are shown.");
        return rawAccess;
    }

    private void addPreview(TraceNodeVO node, String label, String value, String hash, Integer length) {
        if (!StringUtils.hasText(value) && !StringUtils.hasText(hash) && length == null) {
            return;
        }
        TracePreviewItemVO item = new TracePreviewItemVO();
        item.setLabel(label);
        item.setValue(maskText(value, 160));
        item.setHash(hash);
        item.setLength(length);
        item.setDisplayPolicy(StringUtils.hasText(value) ? "MASKED_PREVIEW" : "HASH_LENGTH_ONLY");
        node.getPreviews().add(item);
    }

    private TraceLinkVO link(String label, String path, Map<String, Object> query) {
        TraceLinkVO link = new TraceLinkVO();
        link.setLabel(label);
        Map<String, Object> to = new LinkedHashMap<>();
        to.put("path", path);
        if (query != null && !query.isEmpty()) {
            to.put("query", query);
        }
        link.setTo(to);
        return link;
    }

    private Map<String, Object> queryMap(Object... keyValues) {
        Map<String, Object> query = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                query.put(String.valueOf(keyValues[i]), value);
            }
        }
        return query;
    }

    private TraceModuleStatusVO moduleStatus(String module, String moduleName, String status, Integer count, String message, String errorMessage) {
        TraceModuleStatusVO vo = new TraceModuleStatusVO();
        vo.setModule(module);
        vo.setModuleName(moduleName);
        vo.setStatus(status);
        vo.setCount(count);
        vo.setMessage(message);
        vo.setErrorMessage(errorMessage);
        return vo;
    }

    private List<String[]> moduleDefinitions() {
        return List.of(
                new String[]{MOD_AI_CALL, "AI logs"},
                new String[]{MOD_AGENT_RUN, "Agent runs"},
                new String[]{MOD_AGENT_TASK, "Agent tasks"},
                new String[]{MOD_AGENT_WEEK_PLAN, "Agent week plans"},
                new String[]{MOD_AGENT_WEEK_PLAN_ITEM, "Agent week plan items"},
                new String[]{MOD_ASYNC_TASK, "Async tasks"},
                new String[]{MOD_APPLICATION_PACKAGE, "Application packages"},
                new String[]{MOD_INTERVIEW_SESSION, "Interview sessions"},
                new String[]{MOD_INTERVIEW_REPORT, "Interview reports"},
                new String[]{MOD_INTERVIEW_VOICE, "Interview voice submissions"},
                new String[]{MOD_CONTEXT_USAGE_REFERENCE, "Knowledge/memory usage references"}
        );
    }

    private TraceRiskVO risk(String id, String type, String level, String title, String description, String nodeId) {
        TraceRiskVO risk = new TraceRiskVO();
        risk.setId(id);
        risk.setType(type);
        risk.setLevel(level);
        risk.setTitle(title);
        risk.setDescription(description);
        risk.setNodeId(nodeId);
        return risk;
    }

    private TraceGovernanceSuggestionVO suggestion(String id, String actionType, String title, String reason,
                                                   String riskLevel, TraceNodeVO node) {
        TraceGovernanceSuggestionVO suggestion = new TraceGovernanceSuggestionVO();
        suggestion.setId(id);
        suggestion.setActionType(actionType);
        suggestion.setTitle(title);
        suggestion.setReason(reason);
        suggestion.setRiskLevel(riskLevel);
        suggestion.setNodeId(node.getId());
        suggestion.setTargetType(node.getNodeType());
        suggestion.setTargetId(node.getSourceId());
        suggestion.setLink(firstLink(node));
        suggestion.setExecutableInCockpit(false);
        return suggestion;
    }

    private String actionFor(TraceNodeVO node) {
        if (MOD_ASYNC_TASK.equals(node.getNodeType())) {
            return "VIEW_ASYNC_TASK";
        }
        if (MOD_AGENT_RUN.equals(node.getNodeType()) || MOD_AGENT_TASK.equals(node.getNodeType())) {
            return "VIEW_AGENT_RUN";
        }
        if (MOD_AI_CALL.equals(node.getNodeType())) {
            return "VIEW_AI_LOG";
        }
        if (MOD_INTERVIEW_VOICE.equals(node.getNodeType())) {
            return "VIEW_INTERVIEW_SESSION";
        }
        return "OPEN_SOURCE_RECORD";
    }

    private String failureRiskType(TraceNodeVO node) {
        if (MOD_AI_CALL.equals(node.getNodeType())) {
            return "AI_FAILURE";
        }
        if (MOD_ASYNC_TASK.equals(node.getNodeType())) {
            return "ASYNC_TASK_FAILURE";
        }
        if (MOD_AGENT_RUN.equals(node.getNodeType()) || MOD_AGENT_TASK.equals(node.getNodeType())) {
            return "AGENT_FAILURE";
        }
        return "PARTIAL_RESULT";
    }

    private TraceLinkVO firstLink(TraceNodeVO node) {
        return node.getLinks() == null || node.getLinks().isEmpty() ? null : node.getLinks().get(0);
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String column, Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return;
        }
        sql.append(" AND ").append(column).append(" = ?");
        args.add(value);
    }

    private void appendTimeRange(StringBuilder sql, List<Object> args, String column, LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null) {
            sql.append(" AND ").append(column).append(" >= ?");
            args.add(startTime);
        }
        if (endTime != null) {
            sql.append(" AND ").append(column).append(" <= ?");
            args.add(endTime);
        }
    }

    private void appendIn(StringBuilder sql, List<Object> args, String column, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        sql.append(" AND ").append(column).append(" IN (");
        sql.append(ids.stream().map(id -> "?").collect(Collectors.joining(",")));
        sql.append(")");
        args.addAll(ids);
    }

    private void appendBusinessAny(StringBuilder sql, List<Object> args, Map<String, Set<Long>> columnIds) {
        List<String> clauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        columnIds.forEach((column, ids) -> {
            if (ids != null && !ids.isEmpty()) {
                clauses.add(column + " IN (" + ids.stream().map(id -> "?").collect(Collectors.joining(",")) + ")");
                values.addAll(ids);
            }
        });
        if (clauses.isEmpty()) {
            return;
        }
        sql.append(" AND (").append(String.join(" OR ", clauses)).append(")");
        args.addAll(values);
    }

    private Set<Long> sourceIds(Collection<TraceNodeVO> nodes, String nodeType) {
        return nodes.stream()
                .filter(node -> nodeType.equals(node.getNodeType()))
                .map(TraceNodeVO::getSourceId)
                .filter(StringUtils::hasText)
                .map(this::parseLong)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean sameMeta(TraceNodeVO left, TraceNodeVO right, String key) {
        Object leftValue = left.getMeta().get(key);
        Object rightValue = right.getMeta().get(key);
        return leftValue != null && rightValue != null && Objects.equals(String.valueOf(leftValue), String.valueOf(rightValue));
    }

    private void putMeta(TraceNodeVO node, String key, Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return;
        }
        node.getMeta().put(key, value);
    }

    private Long metaLong(TraceNodeVO node, String key) {
        return parseLong(node.getMeta().get(key));
    }

    private Integer countFor(List<TraceModuleStatusVO> statuses, String module) {
        return statuses.stream()
                .filter(item -> module.equals(item.getModule()))
                .map(TraceModuleStatusVO::getCount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String normalizeStatus(Object value, boolean fallback) {
        if (fallback) {
            return "FALLBACK";
        }
        String status = value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if ("1".equals(status) || "SUCCESS".equals(status) || "DONE".equals(status) || "COMPLETED".equals(status) || "READY".equals(status)) {
            return "SUCCESS";
        }
        if ("0".equals(status) || status.contains("FAIL") || status.contains("ERROR") || status.contains("DEAD")) {
            return "FAILED";
        }
        if (status.contains("RUN") || "PROCESSING".equals(status)) {
            return "RUNNING";
        }
        if (status.contains("PENDING") || status.contains("TODO") || "DRAFT".equals(status)) {
            return "PENDING";
        }
        if (status.contains("SKIP") || status.contains("DEFER")) {
            return "SKIPPED";
        }
        if ("APPLIED".equals(status) || "ARCHIVED".equals(status)) {
            return "SUCCESS";
        }
        return StringUtils.hasText(status) ? "UNKNOWN" : "UNKNOWN";
    }

    private String normalizeInterviewVoiceStatus(String voiceStatus, String transcriptStatus, boolean fallback) {
        if (fallback) {
            return "FALLBACK";
        }
        String voice = voiceStatus == null ? "" : voiceStatus.trim().toUpperCase(Locale.ROOT);
        String transcript = transcriptStatus == null ? "" : transcriptStatus.trim().toUpperCase(Locale.ROOT);
        if ("DISCARDED".equals(voice) || "REJECTED".equals(transcript)) {
            return "SKIPPED";
        }
        if ("CONFIRMED".equals(voice) || "CONFIRMED".equals(transcript)) {
            return "SUCCESS";
        }
        if ("TRANSCRIBING".equals(voice)) {
            return "RUNNING";
        }
        if ("UPLOADED".equals(voice) || "DRAFT".equals(transcript) || "LOW_CONFIDENCE".equals(transcript)) {
            return "PENDING";
        }
        if ("TRANSCRIBE_FAILED".equals(voice) || "FAILED".equals(transcript)) {
            return "FALLBACK";
        }
        return normalizeStatus(firstText(transcript, voice), false);
    }

    private boolean containsFallback(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("fallback") || lower.contains("mock") || lower.contains("degraded") || lower.contains("->");
    }

    private String maskText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String masked = EMAIL_PATTERN.matcher(value).replaceAll("[email]");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[phone]");
        masked = TOKEN_PATTERN.matcher(masked).replaceAll("$1=[secret]");
        return safeText(masked, maxLength);
    }

    private String safeText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength)) + "...";
    }

    private String safeError(Exception ex) {
        return safeText(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 180);
    }

    private Object firstNonNull(Object... values) {
        return Arrays.stream(values).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String firstText(String... values) {
        return Arrays.stream(values).filter(StringUtils::hasText).findFirst().orElse(null);
    }

    private Long sum(Long left, Long right) {
        long total = 0L;
        boolean hasValue = false;
        if (left != null) {
            total += left;
            hasValue = true;
        }
        if (right != null) {
            total += right;
            hasValue = true;
        }
        return hasValue ? total : null;
    }

    private Integer firstInt(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Integer value = asInteger(row, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private LocalDateTime firstDateTime(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            LocalDateTime value = asDateTime(row, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object rowValue(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (row.containsKey(lower)) {
            return row.get(lower);
        }
        String snake = key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        return row.get(snake);
    }

    private String asString(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Map<String, Object> row, String key) {
        return parseLong(rowValue(row, key));
    }

    private Integer asInteger(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        Long parsed = parseLong(value);
        return parsed == null ? null : parsed.intValue();
    }

    private Boolean asBoolean(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private BigDecimal asBigDecimal(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (!StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime asDateTime(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ModuleLoad(String module, String moduleName, String status, List<TraceNodeVO> nodes,
                              String message, String errorMessage) {
    }

    private record Association(String type, String confidence, String reason) {
    }

    private static class TraceQuery {
        private String keyword;
        private String lookupType;
        private String traceId;
        private String requestId;
        private String businessId;
        private String businessType;
        private String bizType;
        private String bizId;
        private Long userId;
        private Long agentRunId;
        private Long asyncTaskId;
        private String messageId;
        private String scene;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean defaultTimeWindowApplied;
        private int limit;

        static TraceQuery from(TraceCockpitQueryDTO dto) {
            TraceCockpitQueryDTO source = dto == null ? new TraceCockpitQueryDTO() : dto;
            TraceQuery query = new TraceQuery();
            query.lookupType = trim(source.getLookupType());
            query.keyword = trim(source.getKeyword());
            query.traceId = trim(source.getTraceId());
            query.requestId = trim(source.getRequestId());
            query.businessId = trim(source.getBusinessId());
            query.businessType = trim(firstText(source.getBusinessType(), source.getBizType()));
            query.bizType = trim(source.getBizType());
            query.bizId = trim(source.getBizId());
            query.userId = source.getUserId();
            query.agentRunId = source.getAgentRunId();
            query.asyncTaskId = source.getAsyncTaskId();
            query.messageId = trim(source.getMessageId());
            query.scene = trim(source.getScene());
            query.startTime = source.getStartTime();
            query.endTime = source.getEndTime();
            query.limit = clampLimit(source.getPageSize());
            query.applyKeyword();
            query.applyDefaultTimeWindow();
            if (!StringUtils.hasText(query.businessId) && StringUtils.hasText(query.bizId)) {
                query.businessId = query.bizId;
            }
            if (!StringUtils.hasText(query.bizId) && StringUtils.hasText(query.bizType) && StringUtils.hasText(query.businessId)) {
                query.bizId = query.businessId;
            }
            return query;
        }

        private void applyKeyword() {
            if (!StringUtils.hasText(keyword) || !StringUtils.hasText(lookupType) || "auto".equalsIgnoreCase(lookupType)) {
                return;
            }
            if ("traceId".equalsIgnoreCase(lookupType) && !StringUtils.hasText(traceId)) {
                traceId = keyword;
            } else if ("requestId".equalsIgnoreCase(lookupType) && !StringUtils.hasText(requestId)) {
                requestId = keyword;
            } else if ("businessId".equalsIgnoreCase(lookupType) && !StringUtils.hasText(businessId)) {
                businessId = keyword;
            } else if ("biz".equalsIgnoreCase(lookupType) && !StringUtils.hasText(bizId)) {
                bizId = keyword;
            } else if ("messageId".equalsIgnoreCase(lookupType) && !StringUtils.hasText(messageId)) {
                messageId = keyword;
            } else if ("agentRunId".equalsIgnoreCase(lookupType) && agentRunId == null) {
                agentRunId = parseLongStatic(keyword);
            } else if ("asyncTaskId".equalsIgnoreCase(lookupType) && asyncTaskId == null) {
                asyncTaskId = parseLongStatic(keyword);
            } else if ("userTime".equalsIgnoreCase(lookupType) && userId == null) {
                userId = parseLongStatic(keyword);
            }
        }

        private void applyDefaultTimeWindow() {
            if (startTime != null || endTime != null || hasExactLookupSeed()) {
                return;
            }
            if (userId == null && !StringUtils.hasText(scene)) {
                return;
            }
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(DEFAULT_TIME_WINDOW_DAYS);
            defaultTimeWindowApplied = true;
        }

        private boolean hasExactLookupSeed() {
            return StringUtils.hasText(traceId)
                    || StringUtils.hasText(requestId)
                    || StringUtils.hasText(businessId)
                    || StringUtils.hasText(bizId)
                    || StringUtils.hasText(messageId)
                    || agentRunId != null
                    || asyncTaskId != null;
        }

        private boolean hasSearchSeed() {
            return StringUtils.hasText(traceId)
                    || StringUtils.hasText(requestId)
                    || StringUtils.hasText(businessId)
                    || StringUtils.hasText(bizId)
                    || StringUtils.hasText(messageId)
                    || StringUtils.hasText(scene)
                    || userId != null
                    || agentRunId != null
                    || asyncTaskId != null
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasAiCallCriteria() {
            return StringUtils.hasText(traceId)
                    || StringUtils.hasText(requestId)
                    || StringUtils.hasText(businessId)
                    || StringUtils.hasText(scene)
                    || userId != null
                    || agentRunId != null
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasAgentRunCriteria() {
            return StringUtils.hasText(traceId)
                    || userId != null
                    || agentRunId != null
                    || isBusinessType("AGENT_RUN", "TARGET_JOB")
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasAgentTaskCriteria(Collection<Long> runIds) {
            return (runIds != null && !runIds.isEmpty())
                    || StringUtils.hasText(traceId)
                    || userId != null
                    || isBusinessType("AGENT_TASK")
                    || (StringUtils.hasText(bizType) && StringUtils.hasText(bizId))
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasAgentWeekPlanCriteria() {
            return StringUtils.hasText(traceId)
                    || userId != null
                    || agentRunId != null
                    || isBusinessType(MOD_AGENT_WEEK_PLAN, MOD_AGENT_RUN, "TARGET_JOB")
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasAgentWeekPlanItemCriteria(Collection<Long> weekPlanIds) {
            return (weekPlanIds != null && !weekPlanIds.isEmpty())
                    || StringUtils.hasText(traceId)
                    || userId != null
                    || isBusinessType(MOD_AGENT_WEEK_PLAN_ITEM, MOD_AGENT_WEEK_PLAN, MOD_AGENT_TASK)
                    || (StringUtils.hasText(bizType) && StringUtils.hasText(bizId))
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasWeekPlanInfluenceCriteria(Collection<Long> weekPlanIds, Collection<Long> itemIds) {
            return (weekPlanIds != null && !weekPlanIds.isEmpty())
                    || (itemIds != null && !itemIds.isEmpty())
                    || StringUtils.hasText(traceId)
                    || userId != null
                    || startTime != null
                    || endTime != null
                    || isBusinessType(MOD_AGENT_WEEK_PLAN, MOD_AGENT_WEEK_PLAN_ITEM, MOD_AGENT_TASK,
                    MOD_APPLICATION_PACKAGE, MOD_INTERVIEW_REPORT, MOD_KNOWLEDGE_DOCUMENT, MOD_KNOWLEDGE_CHUNK, MOD_MEMORY);
        }

        private boolean hasAsyncTaskCriteria() {
            return asyncTaskId != null
                    || StringUtils.hasText(messageId)
                    || StringUtils.hasText(traceId)
                    || userId != null
                    || (StringUtils.hasText(bizType) && StringUtils.hasText(bizId))
                    || (StringUtils.hasText(businessType) && StringUtils.hasText(businessId))
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasApplicationPackageCriteria() {
            return StringUtils.hasText(traceId)
                    || userId != null
                    || isBusinessType("APPLICATION_PACKAGE", "JOB_APPLICATION_PACKAGE", "JOB_APPLICATION", "APPLICATION",
                    "MATCH_REPORT", "RESUME_MATCH_REPORT", "JD_ANALYSIS", "JOB_DESCRIPTION_ANALYSIS",
                    "RESUME_VERSION", "TARGET_JOB")
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasInterviewSessionCriteria(BusinessSeed businessSeed) {
            return userId != null
                    || isBusinessType("INTERVIEW_SESSION", "INTERVIEW", "JOB_APPLICATION", "APPLICATION")
                    || (businessSeed != null && (!businessSeed.applicationPackageIds.isEmpty()
                    || !businessSeed.applicationIds.isEmpty()
                    || !businessSeed.jdAnalysisIds.isEmpty()
                    || !businessSeed.resumeVersionIds.isEmpty()
                    || !businessSeed.matchReportIds.isEmpty()
                    || !businessSeed.targetJobIds.isEmpty()))
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasInterviewReportCriteria(Collection<Long> sessionIds) {
            return userId != null
                    || isBusinessType("INTERVIEW_REPORT", "INTERVIEW_SESSION", "INTERVIEW")
                    || (sessionIds != null && !sessionIds.isEmpty())
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasInterviewVoiceCriteria(Collection<Long> sessionIds) {
            return StringUtils.hasText(traceId)
                    || userId != null
                    || isBusinessType(MOD_INTERVIEW_VOICE, "INTERVIEW_TRANSCRIPT", "INTERVIEW_SESSION", "INTERVIEW")
                    || (sessionIds != null && !sessionIds.isEmpty())
                    || startTime != null
                    || endTime != null;
        }

        private boolean hasUsageReferenceCriteria(Map<String, TraceNodeVO> nodesById) {
            return hasDirectUsageReferenceCriteria()
                    || (nodesById != null && nodesById.values().stream()
                    .anyMatch(node -> isUsageConsumerTypeName(node.getNodeType())));
        }

        private boolean hasDirectUsageReferenceCriteria() {
            return StringUtils.hasText(traceId)
                    || userId != null
                    || startTime != null
                    || endTime != null
                    || isBusinessType("KNOWLEDGE_DOCUMENT", "PERSONAL_KNOWLEDGE_DOCUMENT",
                    "KNOWLEDGE_CHUNK", "PERSONAL_KNOWLEDGE_CHUNK", "MEMORY", "AGENT_MEMORY",
                    "AI_CALL", "AGENT_RUN", "AGENT_TASK", "AGENT_WEEK_PLAN", "AGENT_WEEK_PLAN_ITEM",
                    "APPLICATION_PACKAGE", "INTERVIEW_REPORT");
        }

        private boolean isBusinessType(String... expected) {
            if (!StringUtils.hasText(businessType)) {
                return false;
            }
            return Arrays.stream(expected).anyMatch(item -> item.equalsIgnoreCase(businessType));
        }

        private Long businessIdAsLong() {
            return parseLongStatic(businessId);
        }

        private Long bizIdAsLong() {
            return parseLongStatic(bizId);
        }

        private static int clampLimit(Long pageSize) {
            if (pageSize == null || pageSize <= 0) {
                return DEFAULT_LIMIT;
            }
            return Math.min(pageSize.intValue(), MAX_LIMIT);
        }

        private static String trim(String value) {
            return StringUtils.hasText(value) ? value.trim() : null;
        }

        private static String firstText(String... values) {
            return Arrays.stream(values).filter(StringUtils::hasText).findFirst().orElse(null);
        }

        private static Long parseLongStatic(Object value) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value == null || !StringUtils.hasText(String.valueOf(value))) {
                return null;
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static boolean isUsageConsumerTypeName(String value) {
            return "AI_CALL".equals(value)
                    || "AGENT_RUN".equals(value)
                    || "AGENT_TASK".equals(value)
                    || "AGENT_WEEK_PLAN".equals(value)
                    || "AGENT_WEEK_PLAN_ITEM".equals(value)
                    || "APPLICATION_PACKAGE".equals(value)
                    || "INTERVIEW_REPORT".equals(value);
        }
    }

    private static class BusinessSeed {
        private final Set<Long> applicationPackageIds = new LinkedHashSet<>();
        private final Set<Long> applicationIds = new LinkedHashSet<>();
        private final Set<Long> jdAnalysisIds = new LinkedHashSet<>();
        private final Set<Long> resumeVersionIds = new LinkedHashSet<>();
        private final Set<Long> matchReportIds = new LinkedHashSet<>();
        private final Set<Long> targetJobIds = new LinkedHashSet<>();

        static BusinessSeed fromNodes(TraceQuery seed, Collection<TraceNodeVO> nodes) {
            BusinessSeed businessSeed = new BusinessSeed();
            if (seed.isBusinessType("APPLICATION_PACKAGE", "JOB_APPLICATION_PACKAGE")) {
                addIfNotNull(businessSeed.applicationPackageIds, seed.businessIdAsLong());
            }
            if (seed.isBusinessType("JOB_APPLICATION", "APPLICATION")) {
                addIfNotNull(businessSeed.applicationIds, seed.businessIdAsLong());
            }
            if (seed.isBusinessType("JD_ANALYSIS", "JOB_DESCRIPTION_ANALYSIS")) {
                addIfNotNull(businessSeed.jdAnalysisIds, seed.businessIdAsLong());
            }
            if (seed.isBusinessType("RESUME_VERSION")) {
                addIfNotNull(businessSeed.resumeVersionIds, seed.businessIdAsLong());
            }
            if (seed.isBusinessType("MATCH_REPORT", "RESUME_MATCH_REPORT")) {
                addIfNotNull(businessSeed.matchReportIds, seed.businessIdAsLong());
            }
            if (seed.isBusinessType("TARGET_JOB")) {
                addIfNotNull(businessSeed.targetJobIds, seed.businessIdAsLong());
            }
            for (TraceNodeVO node : nodes) {
                addIfNotNull(businessSeed.applicationPackageIds, TraceQuery.parseLongStatic(node.getMeta().get("applicationPackageId")));
                addIfNotNull(businessSeed.applicationIds, TraceQuery.parseLongStatic(node.getMeta().get("applicationId")));
                addIfNotNull(businessSeed.jdAnalysisIds, TraceQuery.parseLongStatic(node.getMeta().get("jdAnalysisId")));
                addIfNotNull(businessSeed.resumeVersionIds, TraceQuery.parseLongStatic(node.getMeta().get("resumeVersionId")));
                addIfNotNull(businessSeed.matchReportIds, TraceQuery.parseLongStatic(node.getMeta().get("matchReportId")));
                addIfNotNull(businessSeed.targetJobIds, TraceQuery.parseLongStatic(node.getMeta().get("targetJobId")));
            }
            return businessSeed;
        }

        private static void addIfNotNull(Set<Long> ids, Long id) {
            if (id != null) {
                ids.add(id);
            }
        }
    }
}
