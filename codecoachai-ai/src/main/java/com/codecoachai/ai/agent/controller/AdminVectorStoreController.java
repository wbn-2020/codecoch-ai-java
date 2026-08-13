package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.config.KnowledgeProperties;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeVectorRebuildVO;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.common.vector.config.VectorStoreProperties;
import com.codecoachai.common.vector.domain.VectorCollectionInfo;
import com.codecoachai.common.vector.service.VectorIndexJobService;
import com.codecoachai.common.vector.service.VectorStoreClient;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping({"/admin/vector-store", "/admin/analytics/vector-store"})
public class AdminVectorStoreController {

    private static final String QUESTION_COLLECTION = "question_embedding";
    private static final String VECTOR_VIEW_PERMISSION = "admin:analytics:ai";
    private static final String VECTOR_MAINTENANCE_PERMISSION = "admin:question:embedding:rebuild";
    private static final String VECTOR_JOB_KNOWLEDGE_REBUILD = "KNOWLEDGE_REBUILD";
    private static final String VECTOR_JOB_KNOWLEDGE_RETRY = "KNOWLEDGE_RETRY";
    private static final String VECTOR_SCOPE_KNOWLEDGE = "KNOWLEDGE";
    private static final String VECTOR_SCOPE_FAILED_OR_STALE = "FAILED_OR_STALE";
    private static final String COLLECTION_STATE_HEALTHY = "HEALTHY";
    private static final String COLLECTION_STATE_NOT_REQUIRED = "NOT_REQUIRED";
    private static final String COLLECTION_STATE_INITIALIZATION_REQUIRED = "INITIALIZATION_REQUIRED";
    private static final String COLLECTION_STATE_DISABLED = "DISABLED";
    private static final String COLLECTION_STATE_ERROR = "ERROR";
    private static final String OPERATION_QUESTION_REBUILD = "QUESTION_REBUILD";
    private static final String OPERATION_QUESTION_RETRY = "QUESTION_RETRY";
    private static final String OPERATION_DELETE_OUTBOX_RETRY = "DELETE_OUTBOX_RETRY";
    private static final Map<String, String> MYSQL_VECTOR_INDEX_TABLES = Map.of(
            "question_embedding", "question_id",
            "personal_knowledge_chunk", "id"
    );
    private static final String SAFE_IDENTIFIER_PATTERN = "[A-Za-z0-9_]+";

    private final VectorStoreClient vectorStoreClient;
    private final VectorIndexJobService vectorIndexJobService;
    private final V4AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;
    private final JdbcTemplate jdbcTemplate;
    private final AgentV4OpsService agentV4OpsService;
    private final KnowledgeProperties knowledgeProperties;
    private final VectorStoreProperties vectorStoreProperties;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        permissionGuard.require(VECTOR_VIEW_PERMISSION);
        VectorHealthSnapshot snapshot = vectorHealthSnapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", snapshot.enabled());
        result.put("status", snapshot.status());
        result.put("checks", snapshot.checks());
        result.put("config", vectorRuntimeConfig());
        result.put("collections", snapshot.collections());
        result.put("collectionStates", snapshot.collectionStates());
        result.put("maintenance", snapshot.maintenance());
        result.put("deleteOutbox", snapshot.deleteOutbox());
        result.put("embeddingMetrics", embeddingMetrics());
        result.put("mysqlIndexes", snapshot.mysqlIndexes());
        result.put("generatedAt", LocalDateTime.now().toString());
        return Result.success(result);
    }

    @GetMapping("/failures")
    public Result<Map<String, Object>> failures(@RequestParam(required = false) String type,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Integer limit) {
        permissionGuard.require(VECTOR_VIEW_PERMISSION);
        String normalizedType = normalizeFailureType(type);
        List<String> statuses = normalizeFailureStatuses(status);
        int size = clampFailureLimit(limit);
        List<String> errors = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", normalizedType);
        result.put("status", statuses.size() == 1 ? statuses.get(0) : "ALL");
        result.put("statuses", statuses);
        result.put("limit", size);
        result.put("questionFailures", includeFailureType(normalizedType, "question")
                ? questionVectorFailures(statuses, size, errors) : List.of());
        result.put("knowledgeFailures", includeFailureType(normalizedType, "knowledge")
                ? knowledgeVectorFailures(statuses, size, errors) : List.of());
        result.put("deleteOutboxFailures", includeFailureType(normalizedType, "deleteOutbox")
                ? deleteOutboxFailures(statuses, size, errors) : List.of());
        result.put("errors", errors);
        result.put("generatedAt", LocalDateTime.now().toString());
        return Result.success(result);
    }

    @PostMapping("/delete-outbox/retry")
    @OperationLog(module = "vector", action = "DELETE_OUTBOX_RETRY", logArgs = false, logResponse = false)
    public Result<Map<String, Object>> retryVectorDeletes(@RequestParam(required = false) Integer limit,
                                                          @RequestParam(required = false) Boolean confirm,
                                                          @RequestParam(required = false) String reason,
                                                          @RequestParam(required = false) Boolean dryRun,
                                                          @RequestParam(required = false) String idempotencyKey) {
        permissionGuard.require(VECTOR_MAINTENANCE_PERMISSION);
        String cleanReason = cleanReason(reason);
        String cleanIdempotencyKey = cleanIdempotencyKey(idempotencyKey);
        if (requiresMaintenancePreview(confirm, cleanReason, dryRun, cleanIdempotencyKey)) {
            return Result.success(vectorMaintenancePreview(OPERATION_DELETE_OUTBOX_RETRY, limit, cleanReason, cleanIdempotencyKey));
        }
        requireMaintenanceReady(OPERATION_DELETE_OUTBOX_RETRY);
        String lockKey = acquireMaintenanceIdempotencyKey(OPERATION_DELETE_OUTBOX_RETRY, cleanReason, cleanIdempotencyKey);
        Long jobId = vectorIndexJobService.start(OPERATION_DELETE_OUTBOX_RETRY, "DELETE_OUTBOX", null, limit);
        try {
            Map<String, Object> result = new LinkedHashMap<>(retryVectorDeletesInternal(limit));
            attachVectorMaintenanceConfirmation(result, OPERATION_DELETE_OUTBOX_RETRY, limit, cleanReason, cleanIdempotencyKey);
            vectorIndexJobService.finish(jobId, "SUCCESS", result,
                    numberValue(result.get("matched")), numberValue(result.get("deleted")), numberValue(result.get("failed")),
                    numberValue(result.get("deleted")), 0L, null);
            vectorIndexJobService.attach(result, jobId);
            return Result.success(result);
        } catch (Exception ex) {
            releaseMaintenanceIdempotencyKey(lockKey);
            vectorIndexJobService.fail(jobId, ex);
            throw ex;
        }
    }

    @PostMapping("/knowledge/rebuild")
    @OperationLog(module = "vector", action = "KNOWLEDGE_REBUILD", logArgs = false, logResponse = false)
    public Result<KnowledgeVectorRebuildVO> rebuildKnowledgeVectors(@RequestParam(required = false) Integer limit,
                                                                    @RequestParam(required = false) Boolean confirm,
                                                                    @RequestParam(required = false) String reason,
                                                                    @RequestParam(required = false) Boolean dryRun,
                                                                    @RequestParam(required = false) String idempotencyKey) {
        permissionGuard.require(VECTOR_MAINTENANCE_PERMISSION);
        String cleanReason = cleanReason(reason);
        String cleanIdempotencyKey = cleanIdempotencyKey(idempotencyKey);
        if (requiresMaintenancePreview(confirm, cleanReason, dryRun, cleanIdempotencyKey)) {
            return Result.success(knowledgeVectorPreview(VECTOR_JOB_KNOWLEDGE_REBUILD, limit, cleanReason, cleanIdempotencyKey));
        }
        requireMaintenanceReady(VECTOR_JOB_KNOWLEDGE_REBUILD);
        String lockKey = acquireMaintenanceIdempotencyKey(VECTOR_JOB_KNOWLEDGE_REBUILD, cleanReason, cleanIdempotencyKey);
        Long jobId = vectorIndexJobService.start(VECTOR_JOB_KNOWLEDGE_REBUILD, VECTOR_SCOPE_KNOWLEDGE, null, limit);
        try {
            KnowledgeVectorRebuildVO result = agentV4OpsService.rebuildAllKnowledgeVectors(limit);
            attachKnowledgeMaintenanceConfirmation(result, VECTOR_JOB_KNOWLEDGE_REBUILD, limit, cleanReason, cleanIdempotencyKey);
            String status = finishKnowledgeVectorJob(jobId, result);
            attachKnowledgeVectorJob(result, jobId, VECTOR_JOB_KNOWLEDGE_REBUILD, VECTOR_SCOPE_KNOWLEDGE, null, status);
            return Result.success(result);
        } catch (Exception ex) {
            releaseMaintenanceIdempotencyKey(lockKey);
            vectorIndexJobService.fail(jobId, ex);
            throw ex;
        }
    }

    @PostMapping("/knowledge/retry-failed")
    @OperationLog(module = "vector", action = "KNOWLEDGE_RETRY_FAILED", logArgs = false, logResponse = false)
    public Result<KnowledgeVectorRebuildVO> retryFailedKnowledgeVectors(@RequestParam(required = false) Integer limit,
                                                                        @RequestParam(required = false) Boolean confirm,
                                                                        @RequestParam(required = false) String reason,
                                                                        @RequestParam(required = false) Boolean dryRun,
                                                                        @RequestParam(required = false) String idempotencyKey) {
        permissionGuard.require(VECTOR_MAINTENANCE_PERMISSION);
        String cleanReason = cleanReason(reason);
        String cleanIdempotencyKey = cleanIdempotencyKey(idempotencyKey);
        if (requiresMaintenancePreview(confirm, cleanReason, dryRun, cleanIdempotencyKey)) {
            return Result.success(knowledgeVectorPreview(VECTOR_JOB_KNOWLEDGE_RETRY, limit, cleanReason, cleanIdempotencyKey));
        }
        requireMaintenanceReady(VECTOR_JOB_KNOWLEDGE_RETRY);
        String lockKey = acquireMaintenanceIdempotencyKey(VECTOR_JOB_KNOWLEDGE_RETRY, cleanReason, cleanIdempotencyKey);
        Long jobId = vectorIndexJobService.start(VECTOR_JOB_KNOWLEDGE_RETRY, VECTOR_SCOPE_KNOWLEDGE, VECTOR_SCOPE_FAILED_OR_STALE, limit);
        try {
            KnowledgeVectorRebuildVO result = agentV4OpsService.retryAllFailedKnowledgeVectors(limit);
            attachKnowledgeMaintenanceConfirmation(result, VECTOR_JOB_KNOWLEDGE_RETRY, limit, cleanReason, cleanIdempotencyKey);
            String status = finishKnowledgeVectorJob(jobId, result);
            attachKnowledgeVectorJob(result, jobId, VECTOR_JOB_KNOWLEDGE_RETRY, VECTOR_SCOPE_KNOWLEDGE,
                    VECTOR_SCOPE_FAILED_OR_STALE, status);
            return Result.success(result);
        } catch (Exception ex) {
            releaseMaintenanceIdempotencyKey(lockKey);
            vectorIndexJobService.fail(jobId, ex);
            throw ex;
        }
    }

    @GetMapping("/jobs")
    public Result<PageResult<Map<String, Object>>> jobs(@RequestParam(required = false) Long jobId,
                                                        @RequestParam(required = false) String jobType,
                                                        @RequestParam(required = false) String scopeType,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) Long pageNo,
                                                        @RequestParam(required = false) Long pageSize) {
        permissionGuard.require(VECTOR_VIEW_PERMISSION);
        return Result.success(vectorIndexJobService.page(jobId, jobType, scopeType, status, pageNo, pageSize));
    }

    private VectorHealthSnapshot vectorHealthSnapshot() {
        boolean enabled = safeVectorEnabled();
        Map<String, Object> mysqlIndexes = mysqlVectorIndexStats();
        Map<String, Object> questionIndexStats = nestedMap(mysqlIndexes.get("questionEmbedding"));
        Map<String, Object> knowledgeIndexStats = nestedMap(mysqlIndexes.get("personalKnowledgeChunk"));
        SourceDataState questionSource = sourceDataCount(
                "SELECT COUNT(1) FROM question WHERE deleted = 0 AND status = 1",
                "启用题目");
        SourceDataState knowledgeSource = sourceDataFromIndexStats(knowledgeIndexStats, "知识库片段");

        Map<String, VectorCollectionInfo> collectionInfoByName = new LinkedHashMap<>();
        collectionInfoByName.put(QUESTION_COLLECTION, safeCollectionInfo(QUESTION_COLLECTION, enabled));
        String knowledgeCollection = knowledgeCollectionName();
        collectionInfoByName.putIfAbsent(knowledgeCollection, safeCollectionInfo(knowledgeCollection, enabled));

        CollectionState questionCollection = assessCollection(
                QUESTION_COLLECTION, "题目语义索引", questionSource,
                collectionInfoByName.get(QUESTION_COLLECTION), enabled);
        CollectionState knowledgeCollectionState = assessCollection(
                knowledgeCollection, "个人知识库索引", knowledgeSource,
                collectionInfoByName.get(knowledgeCollection), enabled);

        Map<String, Object> deleteOutbox = vectorDeleteOutboxStats();
        Map<String, CollectionState> collectionStatesByName = new HashMap<>();
        collectionStatesByName.put(questionCollection.collectionName(), questionCollection);
        collectionStatesByName.put(knowledgeCollectionState.collectionName(), knowledgeCollectionState);

        MaintenanceDecision questionRebuild = rebuildDecision("题库", questionCollection);
        MaintenanceDecision questionRetry = retryDecision("题库", questionCollection);
        MaintenanceDecision knowledgeRebuild = rebuildDecision("个人知识库", knowledgeCollectionState);
        MaintenanceDecision knowledgeRetry = retryDecision("个人知识库", knowledgeCollectionState);
        MaintenanceDecision deleteRetry = deleteRetryDecision(deleteOutbox, collectionStatesByName, enabled);

        boolean collectionsPresent = List.of(questionCollection, knowledgeCollectionState).stream()
                .allMatch(item -> !item.required() || Boolean.TRUE.equals(item.collectionInfo().getExists()));
        boolean allCollectionsPhysicallyPresent = collectionInfoByName.values().stream()
                .allMatch(item -> Boolean.TRUE.equals(item.getExists()));
        List<Integer> requiredDimensions = List.of(questionCollection, knowledgeCollectionState).stream()
                .filter(CollectionState::required)
                .map(CollectionState::collectionInfo)
                .filter(item -> Boolean.TRUE.equals(item.getExists()))
                .map(VectorCollectionInfo::getVectorSize)
                .filter(size -> size != null && size > 0)
                .distinct()
                .toList();
        boolean dimensionMatched = collectionsPresent && requiredDimensions.size() <= 1;

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("enabled", enabled);
        checks.put("collectionsPresent", collectionsPresent);
        checks.put("allCollectionsPhysicallyPresent", allCollectionsPhysicallyPresent);
        checks.put("dimensionMatched", dimensionMatched);
        checks.put("deleteOutboxClear", Boolean.TRUE.equals(deleteOutbox.get("clear")));

        Map<String, Object> collectionStates = new LinkedHashMap<>();
        collectionStates.put("questionEmbedding", collectionStateMap(questionCollection));
        collectionStates.put("personalKnowledgeChunk", collectionStateMap(knowledgeCollectionState));

        Map<String, Object> maintenance = new LinkedHashMap<>();
        maintenance.put("questionRebuild", maintenanceDecisionMap(questionRebuild));
        maintenance.put("questionRetry", maintenanceDecisionMap(questionRetry));
        maintenance.put("knowledgeRebuild", maintenanceDecisionMap(knowledgeRebuild));
        maintenance.put("knowledgeRetry", maintenanceDecisionMap(knowledgeRetry));
        maintenance.put("deleteOutboxRetry", maintenanceDecisionMap(deleteRetry));

        String status = overallHealthStatus(enabled,
                List.of(questionCollection, knowledgeCollectionState), deleteOutbox);
        return new VectorHealthSnapshot(
                enabled,
                status,
                checks,
                new ArrayList<>(collectionInfoByName.values()),
                collectionStates,
                maintenance,
                deleteOutbox,
                mysqlIndexes
        );
    }

    private boolean safeVectorEnabled() {
        try {
            return vectorStoreClient.isEnabled();
        } catch (Exception ex) {
            log.warn("Unable to read vector store enabled state", ex);
            return false;
        }
    }

    private VectorCollectionInfo safeCollectionInfo(String collectionName, boolean enabled) {
        if (!enabled) {
            return VectorCollectionInfo.builder()
                    .collectionName(collectionName)
                    .exists(false)
                    .status(COLLECTION_STATE_DISABLED)
                    .build();
        }
        try {
            VectorCollectionInfo info = vectorStoreClient.collectionInfo(collectionName);
            if (info != null) {
                return info;
            }
            return collectionErrorInfo(collectionName, "向量存储未返回集合状态");
        } catch (Exception ex) {
            log.warn("Unable to read vector collection health collection={}", collectionName, ex);
            return collectionErrorInfo(collectionName, "集合探测失败：" + safeOperationalError(ex));
        }
    }

    private VectorCollectionInfo collectionErrorInfo(String collectionName, String message) {
        return VectorCollectionInfo.builder()
                .collectionName(collectionName)
                .exists(false)
                .status(COLLECTION_STATE_ERROR)
                .errorMessage(message)
                .build();
    }

    private SourceDataState sourceDataCount(String sql, String label) {
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return new SourceDataState(true, count == null ? 0L : count, null);
        } catch (Exception ex) {
            log.warn("Unable to count vector source data label={}", label, ex);
            return new SourceDataState(false, 0L,
                    label + "数量查询失败：" + safeOperationalError(ex));
        }
    }

    private SourceDataState sourceDataFromIndexStats(Map<String, Object> stats, String label) {
        String errorMessage = stringValue(stats.get("errorMessage"));
        if (StringUtils.hasText(errorMessage)) {
            return new SourceDataState(false, 0L, label + "数量不可用：" + errorMessage);
        }
        return new SourceDataState(true, numberValue(stats.get("total")), null);
    }

    private CollectionState assessCollection(String collectionName, String label, SourceDataState source,
                                             VectorCollectionInfo info, boolean enabled) {
        if (!source.available()) {
            return new CollectionState(collectionName, label, true, source, info,
                    COLLECTION_STATE_ERROR,
                    source.errorMessage() + "。请先恢复数据库查询后再执行索引维护。");
        }
        boolean required = source.count() > 0;
        if (!enabled) {
            return new CollectionState(collectionName, label, required, source, info,
                    COLLECTION_STATE_DISABLED,
                    "向量存储未启用。请先检查 codecoachai.vector.enabled、provider 和 Qdrant 连接配置。");
        }
        if (!required && !Boolean.TRUE.equals(info.getExists())) {
            return new CollectionState(collectionName, label, false, source, info,
                    COLLECTION_STATE_NOT_REQUIRED,
                    label + "暂无待索引业务数据，当前不需要创建集合。");
        }
        if (Boolean.TRUE.equals(info.getExists())) {
            if (isCollectionError(info) || info.getVectorSize() == null || info.getVectorSize() <= 0) {
                return new CollectionState(collectionName, label, required, source, info,
                        COLLECTION_STATE_ERROR,
                        label + "状态异常或向量维度无效。请先检查 Qdrant 集合配置和连通性。");
            }
            return new CollectionState(collectionName, label, required, source, info,
                    COLLECTION_STATE_HEALTHY,
                    label + "可用。");
        }
        if (isCollectionMissing(info)) {
            return new CollectionState(collectionName, label, true, source, info,
                    COLLECTION_STATE_INITIALIZATION_REQUIRED,
                    label + "集合缺失且存在 " + source.count()
                            + " 条待索引数据。请通过受控全量重建初始化集合。");
        }
        return new CollectionState(collectionName, label, required, source, info,
                COLLECTION_STATE_ERROR,
                label + "探测异常。请先恢复 Qdrant 连接并确认集合状态后再执行维护。");
    }

    private boolean isCollectionMissing(VectorCollectionInfo info) {
        return info != null
                && !Boolean.TRUE.equals(info.getExists())
                && "NOT_FOUND".equalsIgnoreCase(info.getStatus());
    }

    private boolean isCollectionError(VectorCollectionInfo info) {
        if (info == null) {
            return true;
        }
        String status = info.getStatus();
        return COLLECTION_STATE_ERROR.equalsIgnoreCase(status)
                || (!Boolean.TRUE.equals(info.getExists())
                && !isCollectionMissing(info)
                && !COLLECTION_STATE_DISABLED.equalsIgnoreCase(status));
    }

    private MaintenanceDecision rebuildDecision(String target, CollectionState state) {
        if (!state.source().available()) {
            return blockedDecision(state.state(), state.message());
        }
        if (COLLECTION_STATE_NOT_REQUIRED.equals(state.state())) {
            return blockedDecision(state.state(),
                    target + "暂无可重建的语义索引数据，无需执行全量重建。");
        }
        if (COLLECTION_STATE_DISABLED.equals(state.state()) || COLLECTION_STATE_ERROR.equals(state.state())) {
            return blockedDecision(state.state(), state.message());
        }
        if (COLLECTION_STATE_INITIALIZATION_REQUIRED.equals(state.state())) {
            return allowedDecision(state.state(),
                    state.message() + " 本次全量重建可承担受控初始化。");
        }
        return allowedDecision(state.state(), target + "语义索引满足全量重建条件。");
    }

    private MaintenanceDecision retryDecision(String target, CollectionState state) {
        if (COLLECTION_STATE_HEALTHY.equals(state.state())) {
            return allowedDecision(state.state(), target + "语义索引满足失败重试条件。");
        }
        if (COLLECTION_STATE_INITIALIZATION_REQUIRED.equals(state.state())) {
            return blockedDecision(state.state(),
                    target + "向量集合尚未初始化。请先执行全量重建完成集合初始化，再重试失败任务。");
        }
        if (COLLECTION_STATE_NOT_REQUIRED.equals(state.state())) {
            return blockedDecision(state.state(), target + "暂无待索引数据，无需重试。");
        }
        return blockedDecision(state.state(), state.message());
    }

    private MaintenanceDecision deleteRetryDecision(Map<String, Object> deleteOutbox,
                                                     Map<String, CollectionState> coreStates,
                                                     boolean enabled) {
        if (!enabled) {
            return blockedDecision(COLLECTION_STATE_DISABLED,
                    "向量存储未启用，无法执行删除补偿。请先恢复向量存储配置和连接。");
        }
        String outboxError = stringValue(deleteOutbox.get("errorMessage"));
        if (StringUtils.hasText(outboxError)) {
            return blockedDecision(COLLECTION_STATE_ERROR,
                    "删除补偿队列不可用。请先恢复 vector_delete_outbox 查询后再重试。");
        }
        long retryable = numberValue(deleteOutbox.get("retryable"));
        if (retryable <= 0) {
            return blockedDecision(COLLECTION_STATE_NOT_REQUIRED,
                    "当前没有待处理或失败的删除补偿记录，无需执行重试。");
        }
        Set<String> targetCollections = retryableDeleteCollections(deleteOutbox);
        if (targetCollections.isEmpty()) {
            return blockedDecision(COLLECTION_STATE_ERROR,
                    "无法确定待补偿记录对应的向量集合。请先检查删除补偿队列数据。");
        }
        for (String collectionName : targetCollections) {
            CollectionState coreState = coreStates.get(collectionName);
            VectorCollectionInfo info = coreState == null
                    ? safeCollectionInfo(collectionName, true)
                    : coreState.collectionInfo();
            if (!Boolean.TRUE.equals(info.getExists()) || isCollectionError(info)) {
                return blockedDecision(collectionStateForDelete(info),
                        "删除补偿目标集合 " + collectionName
                                + " 当前不可用。请先恢复或确认该集合，再执行删除补偿。");
            }
        }
        return allowedDecision(COLLECTION_STATE_HEALTHY,
                "删除补偿队列和目标集合状态正常，可执行重试。");
    }

    private Set<String> retryableDeleteCollections(Map<String, Object> deleteOutbox) {
        Set<String> result = new LinkedHashSet<>();
        Object value = deleteOutbox.get("collectionCounts");
        if (!(value instanceof List<?> rows)) {
            return result;
        }
        for (Object rowValue : rows) {
            if (!(rowValue instanceof Map<?, ?> row)) {
                continue;
            }
            String status = stringValue(row.get("status"));
            String collectionName = stringValue(row.get("collectionName"));
            if (StringUtils.hasText(collectionName)
                    && ("PENDING".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status))
                    && numberValue(row.get("count")) > 0) {
                result.add(collectionName);
            }
        }
        return result;
    }

    private String collectionStateForDelete(VectorCollectionInfo info) {
        if (isCollectionMissing(info)) {
            return COLLECTION_STATE_INITIALIZATION_REQUIRED;
        }
        return COLLECTION_STATE_ERROR;
    }

    private MaintenanceDecision allowedDecision(String state, String message) {
        return new MaintenanceDecision(true, state, message);
    }

    private MaintenanceDecision blockedDecision(String state, String message) {
        return new MaintenanceDecision(false, state, message);
    }

    private void requireMaintenanceReady(String operation) {
        VectorHealthSnapshot snapshot = vectorHealthSnapshot();
        String key = switch (operation) {
            case OPERATION_QUESTION_REBUILD -> "questionRebuild";
            case OPERATION_QUESTION_RETRY -> "questionRetry";
            case VECTOR_JOB_KNOWLEDGE_REBUILD -> "knowledgeRebuild";
            case VECTOR_JOB_KNOWLEDGE_RETRY -> "knowledgeRetry";
            case OPERATION_DELETE_OUTBOX_RETRY -> "deleteOutboxRetry";
            default -> null;
        };
        Map<String, Object> decision = key == null ? Map.of() : nestedMap(snapshot.maintenance().get(key));
        if (!Boolean.TRUE.equals(decision.get("allowed"))) {
            String reason = firstText(stringValue(decision.get("message")),
                    "当前语义索引状态不允许执行该维护操作，请先查看健康状态。");
            throw new BusinessException(ErrorCode.SEMANTIC_VALIDATION_ERROR,
                    "语义索引维护已拒绝：" + reason);
        }
    }

    private Map<String, Object> collectionStateMap(CollectionState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collectionName", state.collectionName());
        result.put("label", state.label());
        result.put("state", state.state());
        result.put("required", state.required());
        result.put("sourceCountAvailable", state.source().available());
        result.put("sourceCount", state.source().count());
        result.put("message", state.message());
        result.put("exists", Boolean.TRUE.equals(state.collectionInfo().getExists()));
        result.put("rawStatus", state.collectionInfo().getStatus());
        return result;
    }

    private Map<String, Object> maintenanceDecisionMap(MaintenanceDecision decision) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allowed", decision.allowed());
        result.put("state", decision.state());
        result.put("message", decision.message());
        return result;
    }

    private String overallHealthStatus(boolean enabled, List<CollectionState> collections,
                                       Map<String, Object> deleteOutbox) {
        if (!enabled) {
            return COLLECTION_STATE_DISABLED;
        }
        if (StringUtils.hasText(stringValue(deleteOutbox.get("errorMessage")))
                || collections.stream().anyMatch(item -> COLLECTION_STATE_ERROR.equals(item.state()))) {
            return COLLECTION_STATE_ERROR;
        }
        if (collections.stream().anyMatch(item -> COLLECTION_STATE_INITIALIZATION_REQUIRED.equals(item.state()))) {
            return COLLECTION_STATE_INITIALIZATION_REQUIRED;
        }
        if (numberValue(deleteOutbox.get("failed")) > 0 || numberValue(deleteOutbox.get("pending")) > 0) {
            return "DEGRADED";
        }
        return COLLECTION_STATE_HEALTHY;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String knowledgeCollectionName() {
        String collection = knowledgeProperties.getCollection();
        return StringUtils.hasText(collection) ? collection.trim() : "personal_knowledge_chunk";
    }

    private Map<String, Object> mysqlVectorIndexStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("questionEmbedding", tableIndexStats("question_embedding", "question_id"));
        stats.put("personalKnowledgeChunk", tableIndexStats("personal_knowledge_chunk", "id"));
        return stats;
    }

    private Map<String, Object> vectorRuntimeConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", vectorStoreClient.isEnabled());
        config.put("provider", vectorStoreProperties.getProvider());
        config.put("baseUrlMasked", maskEndpoint(vectorStoreProperties.getBaseUrl()));
        config.put("defaultLimit", vectorStoreProperties.getDefaultLimit());
        config.put("requestTimeout", vectorStoreProperties.getRequestTimeout().toString());
        config.put("knowledgeCollection", knowledgeProperties.getCollection());
        config.put("knowledgeAskMinScore", knowledgeProperties.safeAskMinScore());
        config.put("knowledgeNearDuplicateThreshold", knowledgeProperties.safeNearDuplicateThreshold());
        config.put("knowledgeChunkSize", knowledgeProperties.safeChunkSize());
        config.put("knowledgeChunkOverlap", knowledgeProperties.safeChunkOverlap());
        return config;
    }

    private Map<String, Object> embeddingMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT COUNT(1) AS callCount,
                           SUM(CASE WHEN success = 1 OR status = 1 THEN 1 ELSE 0 END) AS successCount,
                           SUM(CASE WHEN success = 0 OR status = 0 THEN 1 ELSE 0 END) AS failedCount,
                           AVG(elapsed_ms) AS averageElapsedMs,
                           MAX(elapsed_ms) AS maxElapsedMs,
                           SUM(COALESCE(total_tokens, 0)) AS totalTokens,
                           MAX(created_at) AS lastCalledAt
                    FROM ai_call_log
                    WHERE deleted = 0
                      AND scene = 'EMBEDDING'
                      AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                    """);
            Map<String, Object> summary = rows.isEmpty() ? Map.of() : rows.get(0);
            long callCount = numberValue(summary.get("callCount"));
            long failedCount = numberValue(summary.get("failedCount"));
            metrics.put("windowDays", 7);
            metrics.put("callCount", callCount);
            metrics.put("successCount", numberValue(summary.get("successCount")));
            metrics.put("failedCount", failedCount);
            metrics.put("failureRate", callCount == 0 ? 0D : Math.round((failedCount * 10000D) / callCount) / 100D);
            metrics.put("averageElapsedMs", roundedDouble(summary.get("averageElapsedMs")));
            metrics.put("maxElapsedMs", numberValue(summary.get("maxElapsedMs")));
            metrics.put("totalTokens", numberValue(summary.get("totalTokens")));
            metrics.put("lastCalledAt", summary.get("lastCalledAt"));
            metrics.put("modelCounts", jdbcTemplate.queryForList("""
                    SELECT COALESCE(model_name, model, 'UNKNOWN') AS model, COUNT(1) AS count
                    FROM ai_call_log
                    WHERE deleted = 0
                      AND scene = 'EMBEDDING'
                      AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                    GROUP BY COALESCE(model_name, model, 'UNKNOWN')
                    ORDER BY count DESC, model
                    LIMIT 8
                    """));
        } catch (DataAccessException ex) {
            metrics.put("windowDays", 7);
            metrics.put("callCount", 0L);
            metrics.put("failedCount", 0L);
            metrics.put("errorMessage", "ai_call_log embedding metrics are not available: "
                    + safeOperationalError(ex));
        }
        return metrics;
    }

    private List<String> coreCollections() {
        List<String> collections = new ArrayList<>();
        collections.add(QUESTION_COLLECTION);
        String knowledgeCollection = knowledgeProperties.getCollection();
        if (knowledgeCollection != null && !knowledgeCollection.isBlank()
                && !collections.contains(knowledgeCollection)) {
            collections.add(knowledgeCollection);
        }
        return collections;
    }

    private Map<String, Object> tableIndexStats(String tableName, String idColumn) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (!isAllowedVectorIndexTable(tableName, idColumn)) {
            stats.put("tableName", tableName);
            stats.put("idColumn", idColumn);
            stats.put("total", 0L);
            stats.put("statusCounts", List.of());
            stats.put("modelCounts", List.of());
            stats.put("errorMessage", "Unsupported vector index table metadata");
            return stats;
        }
        String quotedTableName = quoteIdentifier(tableName);
        try {
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM " + quotedTableName + " WHERE deleted = 0", Long.class);
            List<Map<String, Object>> statusCounts = jdbcTemplate.queryForList(
                    "SELECT COALESCE(index_status, 'PENDING') AS status, COUNT(1) AS count "
                            + "FROM " + quotedTableName + " WHERE deleted = 0 "
                            + "GROUP BY COALESCE(index_status, 'PENDING') ORDER BY status");
            List<Map<String, Object>> modelCounts = jdbcTemplate.queryForList(
                    "SELECT COALESCE(embedding_model, 'UNKNOWN') AS model, COUNT(1) AS count "
                            + "FROM " + quotedTableName + " WHERE deleted = 0 "
                            + "GROUP BY COALESCE(embedding_model, 'UNKNOWN') ORDER BY count DESC, model LIMIT 8");
            String lastIndexedAt = jdbcTemplate.queryForObject(
                    "SELECT MAX(indexed_at) FROM " + quotedTableName + " WHERE deleted = 0", String.class);
            stats.put("tableName", tableName);
            stats.put("idColumn", idColumn);
            stats.put("total", total == null ? 0L : total);
            stats.put("statusCounts", statusCounts);
            stats.put("modelCounts", modelCounts);
            stats.put("lastIndexedAt", lastIndexedAt);
        } catch (DataAccessException ex) {
            stats.put("tableName", tableName);
            stats.put("idColumn", idColumn);
            stats.put("total", 0L);
            stats.put("statusCounts", List.of());
            stats.put("modelCounts", List.of());
            stats.put("errorMessage", safeOperationalError(ex));
        }
        return stats;
    }

    private boolean isAllowedVectorIndexTable(String tableName, String idColumn) {
        return isSafeIdentifier(tableName)
                && isSafeIdentifier(idColumn)
                && idColumn.equals(MYSQL_VECTOR_INDEX_TABLES.get(tableName));
    }

    private String quoteIdentifier(String identifier) {
        if (!isSafeIdentifier(identifier)) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + identifier);
        }
        return "`" + identifier + "`";
    }

    private boolean isSafeIdentifier(String identifier) {
        return identifier != null && identifier.matches(SAFE_IDENTIFIER_PATTERN);
    }

    private Map<String, Object> vectorDeleteOutboxStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> statusCounts = jdbcTemplate.queryForList("""
                    SELECT status, COUNT(1) AS count
                    FROM vector_delete_outbox
                    WHERE deleted = 0
                    GROUP BY status
                    ORDER BY status
                    """);
            List<Map<String, Object>> collectionCounts = jdbcTemplate.queryForList("""
                    SELECT collection_name AS collectionName, status, COUNT(1) AS count
                    FROM vector_delete_outbox
                    WHERE deleted = 0
                    GROUP BY collection_name, status
                    ORDER BY collection_name, status
                    """);
            long pending = countStatus(statusCounts, "PENDING");
            long failed = countStatus(statusCounts, "FAILED");
            long done = countStatus(statusCounts, "DONE");
            stats.put("pending", pending);
            stats.put("failed", failed);
            stats.put("done", done);
            stats.put("retryable", pending + failed);
            stats.put("clear", pending + failed == 0);
            stats.put("statusCounts", statusCounts);
            stats.put("collectionCounts", collectionCounts);
        } catch (DataAccessException ex) {
            stats.put("pending", 0L);
            stats.put("failed", 0L);
            stats.put("done", 0L);
            stats.put("retryable", 0L);
            stats.put("clear", false);
            stats.put("errorMessage", "vector_delete_outbox is not available: "
                    + safeOperationalError(ex));
        }
        return stats;
    }

    private List<Map<String, Object>> questionVectorFailures(List<String> statuses, int limit, List<String> errors) {
        try {
            List<Object> args = new ArrayList<>(statuses);
            args.add(limit);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT question_id AS questionId,
                           COALESCE(index_status, 'PENDING') AS indexStatus,
                           embedding_model AS embeddingModel,
                           embedding_dimension AS embeddingDimension,
                           indexed_at AS indexedAt,
                           last_error AS lastError,
                           updated_at AS updatedAt
                    FROM question_embedding
                    WHERE deleted = 0
                      AND COALESCE(index_status, 'PENDING') IN (%s)
                    ORDER BY updated_at DESC
                    LIMIT ?
                    """.formatted(sqlPlaceholders(statuses.size())), args.toArray());
            return sanitizeQuestionFailures(rows);
        } catch (DataAccessException ex) {
            errors.add("question_embedding query failed: " + safeOperationalError(ex));
            return List.of();
        }
    }

    private List<Map<String, Object>> knowledgeVectorFailures(List<String> statuses, int limit, List<String> errors) {
        try {
            List<Object> args = new ArrayList<>(statuses);
            args.add(limit);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT id AS chunkId,
                           user_id AS userId,
                           document_id AS documentId,
                           chunk_index AS chunkIndex,
                           COALESCE(index_status, 'PENDING') AS indexStatus,
                           embedding_model AS embeddingModel,
                           embedding_dimension AS embeddingDimension,
                           indexed_at AS indexedAt,
                           last_error AS lastError,
                           updated_at AS updatedAt
                    FROM personal_knowledge_chunk
                    WHERE deleted = 0
                      AND COALESCE(index_status, 'PENDING') IN (%s)
                    ORDER BY updated_at DESC
                    LIMIT ?
                    """.formatted(sqlPlaceholders(statuses.size())), args.toArray());
            return sanitizeKnowledgeFailures(rows);
        } catch (DataAccessException ex) {
            errors.add("personal_knowledge_chunk query failed: " + safeOperationalError(ex));
            return List.of();
        }
    }

    private List<Map<String, Object>> deleteOutboxFailures(List<String> statuses, int limit, List<String> errors) {
        try {
            List<Object> args = new ArrayList<>(statuses);
            args.add(limit);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT collection_name AS collectionName,
                           point_id AS pointId,
                           biz_type AS bizType,
                           status,
                           retry_count AS retryCount,
                           last_error AS lastError,
                           updated_at AS updatedAt
                    FROM vector_delete_outbox
                    WHERE deleted = 0
                      AND status IN (%s)
                    ORDER BY updated_at DESC
                    LIMIT ?
                    """.formatted(sqlPlaceholders(statuses.size())), args.toArray());
            return sanitizeDeleteOutboxFailures(rows);
        } catch (DataAccessException ex) {
            errors.add("vector_delete_outbox query failed: " + safeOperationalError(ex));
            return List.of();
        }
    }

    private String normalizeFailureType(String type) {
        String value = type == null ? "all" : type.trim();
        if ("question".equalsIgnoreCase(value)) {
            return "question";
        }
        if ("knowledge".equalsIgnoreCase(value)) {
            return "knowledge";
        }
        if ("deleteOutbox".equalsIgnoreCase(value)
                || "delete-outbox".equalsIgnoreCase(value)
                || "delete_outbox".equalsIgnoreCase(value)) {
            return "deleteOutbox";
        }
        return "all";
    }

    private boolean includeFailureType(String selectedType, String expectedType) {
        return "all".equals(selectedType) || expectedType.equals(selectedType);
    }

    private List<String> normalizeFailureStatuses(String status) {
        String value = status == null ? "" : status.trim().toUpperCase();
        if ("FAILED".equals(value) || "PENDING".equals(value)) {
            return List.of(value);
        }
        return List.of("FAILED", "PENDING");
    }

    private int clampFailureLimit(Integer limit) {
        return limit == null ? 50 : Math.max(1, Math.min(limit, 200));
    }

    private boolean requiresMaintenancePreview(Boolean confirm, String reason, Boolean dryRun, String idempotencyKey) {
        return Boolean.TRUE.equals(dryRun)
                || !Boolean.TRUE.equals(confirm)
                || !StringUtils.hasText(reason)
                || !StringUtils.hasText(idempotencyKey);
    }

    private String cleanReason(String reason) {
        return operationConfirmationGuard.cleanReason(reason);
    }

    private String cleanIdempotencyKey(String idempotencyKey) {
        return operationConfirmationGuard.cleanIdempotencyKey(idempotencyKey);
    }

    private Map<String, Object> vectorMaintenancePreview(String operation, Integer limit, String reason,
                                                         String idempotencyKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requiresConfirmation", true);
        result.put("dryRun", true);
        result.put("operation", operation);
        result.put("requestedLimit", limit);
        result.put("accessReason", reason);
        result.put("idempotencyKey", idempotencyKey);
        result.put("message", "Submit confirm=true, a non-empty reason, and an idempotencyKey to execute this vector maintenance operation.");
        result.put("deleteOutbox", vectorDeleteOutboxStats());
        return result;
    }

    private KnowledgeVectorRebuildVO knowledgeVectorPreview(String operation, Integer limit, String reason,
                                                            String idempotencyKey) {
        KnowledgeVectorRebuildVO result = new KnowledgeVectorRebuildVO();
        result.setVectorEnabled(vectorStoreClient.isEnabled());
        result.setEmbeddingEnabled(vectorStoreClient.isEnabled());
        result.setSemanticEnabled(vectorStoreClient.isEnabled());
        result.setRequiresConfirmation(true);
        result.setDryRun(true);
        result.setOperation(operation);
        result.setRequestedLimit(limit);
        result.setAccessReason(reason);
        result.setIdempotencyKey(idempotencyKey);
        result.setConfirmationMessage("Submit confirm=true, a non-empty reason, and an idempotencyKey to execute this vector maintenance operation.");
        result.setErrors(List.of());
        return result;
    }

    private void attachVectorMaintenanceConfirmation(Map<String, Object> result, String operation,
                                                     Integer limit, String reason, String idempotencyKey) {
        result.put("requiresConfirmation", false);
        result.put("dryRun", false);
        result.put("operation", operation);
        result.put("requestedLimit", limit);
        result.put("accessReason", reason);
        result.put("idempotencyKey", idempotencyKey);
    }

    private void attachKnowledgeMaintenanceConfirmation(KnowledgeVectorRebuildVO result, String operation,
                                                        Integer limit, String reason, String idempotencyKey) {
        if (result == null) {
            return;
        }
        result.setRequiresConfirmation(false);
        result.setDryRun(false);
        result.setOperation(operation);
        result.setRequestedLimit(limit);
        result.setAccessReason(reason);
        result.setIdempotencyKey(idempotencyKey);
    }

    private String acquireMaintenanceIdempotencyKey(String operation, String reason, String idempotencyKey) {
        return operationConfirmationGuard.requireConfirmed("admin-vector-maintenance:" + operation,
                true, false, reason, idempotencyKey);
    }

    private void releaseMaintenanceIdempotencyKey(String lockKey) {
        operationConfirmationGuard.release(lockKey);
    }

    private List<Map<String, Object>> sanitizeQuestionFailures(List<Map<String, Object>> rows) {
        rows.forEach(row -> row.put("lastError", sanitizeOperationalError(row.get("lastError"))));
        return rows;
    }

    private List<Map<String, Object>> sanitizeKnowledgeFailures(List<Map<String, Object>> rows) {
        rows.forEach(row -> {
            row.put("chunkIdMasked", maskIdentifier(row.remove("chunkId")));
            row.put("userIdMasked", maskIdentifier(row.remove("userId")));
            row.put("documentIdMasked", maskIdentifier(row.remove("documentId")));
            row.put("lastError", sanitizeOperationalError(row.get("lastError")));
        });
        return rows;
    }

    private List<Map<String, Object>> sanitizeDeleteOutboxFailures(List<Map<String, Object>> rows) {
        rows.forEach(row -> {
            row.put("pointIdMasked", maskIdentifier(row.remove("pointId")));
            row.put("lastError", sanitizeOperationalError(row.get("lastError")));
        });
        return rows;
    }

    private String sanitizeOperationalError(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String hash = Integer.toHexString(text.hashCode());
        return "errorRef=" + hash + "; summary=" + operationalErrorSummary(text);
    }

    private String safeOperationalError(Exception ex) {
        return firstText(sanitizeOperationalError(ex == null ? null : ex.getMessage()),
                ex == null ? "operational error occurred" : ex.getClass().getSimpleName());
    }

    private String operationalErrorSummary(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "operation timed out";
        }
        if (lower.contains("connect") || lower.contains("connection") || lower.contains("refused")) {
            return "dependency connection failed";
        }
        if (lower.contains("unauthorized") || lower.contains("forbidden") || lower.contains("permission")) {
            return "dependency authorization failed";
        }
        if (lower.contains("sql") || lower.contains("jdbc") || lower.contains("database")) {
            return "database operation failed";
        }
        return "operational error occurred";
    }

    private String maskIdentifier(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.length() <= 4) {
            return "***" + text;
        }
        return text.substring(0, 2) + "***" + text.substring(text.length() - 2);
    }

    private String maskEndpoint(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            String scheme = StringUtils.hasText(uri.getScheme()) ? uri.getScheme() : "http";
            String host = StringUtils.hasText(uri.getHost()) ? uri.getHost() : "configured";
            String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            return scheme + "://" + maskHost(host) + port + "/***";
        } catch (Exception ex) {
            return "configured/***";
        }
    }

    private String maskHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "***";
        }
        if (host.length() <= 2) {
            return "***";
        }
        return host.charAt(0) + "***" + host.charAt(host.length() - 1);
    }

    private Map<String, Object> retryVectorDeletesInternal(Integer limit) {
        int size = limit == null ? 500 : Math.max(1, Math.min(limit, 5000));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vectorEnabled", vectorStoreClient.isEnabled());
        result.put("requested", size);
        if (!vectorStoreClient.isEnabled()) {
            result.put("matched", 0);
            result.put("deleted", 0);
            result.put("failed", 0);
            result.put("errors", List.of());
            return result;
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList("""
                    SELECT collection_name, point_id
                    FROM vector_delete_outbox
                    WHERE deleted = 0
                      AND status IN ('PENDING', 'FAILED')
                    ORDER BY updated_at ASC
                    LIMIT ?
                    """, size);
        } catch (DataAccessException ex) {
            result.put("matched", 0);
            result.put("deleted", 0);
            result.put("failed", 0);
            result.put("errors", List.of("vector_delete_outbox query failed: "
                    + safeOperationalError(ex)));
            return result;
        }
        Map<String, List<String>> pointIdsByCollection = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String collectionName = stringValue(row.get("collection_name"));
            String pointId = stringValue(row.get("point_id"));
            if (collectionName == null || pointId == null) {
                continue;
            }
            pointIdsByCollection.computeIfAbsent(collectionName, ignored -> new ArrayList<>()).add(pointId);
        }
        int deleted = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : pointIdsByCollection.entrySet()) {
            String collectionName = entry.getKey();
            List<String> pointIds = entry.getValue();
            try {
                vectorStoreClient.delete(collectionName, pointIds);
                markVectorDeletesDone(collectionName, pointIds);
                deleted += pointIds.size();
            } catch (Exception ex) {
                markVectorDeletesFailed(collectionName, pointIds, ex);
                failed += pointIds.size();
                errors.add(collectionName + ": " + safeOperationalError(ex));
                log.warn("Admin vector delete retry failed collection={} pointCount={}", collectionName, pointIds.size(), ex);
            }
        }
        result.put("matched", rows.size());
        result.put("deleted", deleted);
        result.put("failed", failed);
        result.put("errors", errors);
        result.put("deleteOutbox", vectorDeleteOutboxStats());
        return result;
    }

    private void markVectorDeletesDone(String collectionName, List<String> pointIds) {
        jdbcTemplate.update("""
                UPDATE vector_delete_outbox
                SET status = 'DONE', last_error = NULL, updated_at = NOW()
                WHERE collection_name = ? AND point_id IN (%s)
                """.formatted(sqlPlaceholders(pointIds.size())),
                vectorDeleteSqlArgs(collectionName, pointIds).toArray());
    }

    private void markVectorDeletesFailed(String collectionName, List<String> pointIds, Exception ex) {
        jdbcTemplate.update("""
                UPDATE vector_delete_outbox
                SET status = 'FAILED', retry_count = retry_count + 1, last_error = ?, updated_at = NOW()
                WHERE collection_name = ? AND point_id IN (%s)
                """.formatted(sqlPlaceholders(pointIds.size())),
                vectorDeleteSqlArgs(collectionName, pointIds,
                        safeOperationalError(ex)).toArray());
    }

    private String finishKnowledgeVectorJob(Long jobId, KnowledgeVectorRebuildVO result) {
        long total = numberValue(result == null ? null : result.getChunkCount());
        long success = numberValue(result == null ? null : result.getVectorUpdated());
        long failed = result == null || result.getFailedDocuments() == null ? 0L : result.getFailedDocuments().size();
        long updated = numberValue(result == null ? null : result.getVectorUpdated());
        long deleted = numberValue(result == null ? null : result.getVectorDeleted());
        String error = result == null || result.getErrors() == null || result.getErrors().isEmpty()
                ? null : String.join("; ", result.getErrors().stream().limit(5).toList());
        String status = failed > 0 || error != null ? "FAILED" : "SUCCESS";
        vectorIndexJobService.finish(jobId, status,
                Map.of(), total, success, failed, updated, deleted, error);
        return status;
    }

    private void attachKnowledgeVectorJob(KnowledgeVectorRebuildVO result, Long jobId, String jobType,
                                          String scopeType, String scopeId, String status) {
        if (result == null || jobId == null) {
            return;
        }
        result.setJobId(jobId);
        result.setVectorJobId(jobId);
        result.setVectorJobType(jobType);
        result.setVectorScopeType(scopeType);
        result.setVectorScopeId(scopeId);
        result.setVectorJobStatus(status);
    }

    private String sqlPlaceholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private List<Object> vectorDeleteSqlArgs(String collectionName, List<String> pointIds) {
        List<Object> args = new ArrayList<>();
        args.add(collectionName);
        args.addAll(pointIds);
        return args;
    }

    private List<Object> vectorDeleteSqlArgs(String collectionName, List<String> pointIds, String error) {
        List<Object> args = new ArrayList<>();
        args.add(truncateText(error, 512));
        args.add(collectionName);
        args.addAll(pointIds);
        return args;
    }

    private long countStatus(List<Map<String, Object>> rows, String status) {
        return rows.stream()
                .filter(row -> status.equalsIgnoreCase(stringValue(row.get("status"))))
                .map(row -> row.get("count"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToLong(Number::longValue)
                .sum();
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Long roundedDouble(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        return Math.round(number.doubleValue());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String truncateText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record SourceDataState(
            boolean available,
            long count,
            String errorMessage
    ) {
    }

    private record CollectionState(
            String collectionName,
            String label,
            boolean required,
            SourceDataState source,
            VectorCollectionInfo collectionInfo,
            String state,
            String message
    ) {
    }

    private record MaintenanceDecision(
            boolean allowed,
            String state,
            String message
    ) {
    }

    private record VectorHealthSnapshot(
            boolean enabled,
            String status,
            Map<String, Object> checks,
            List<VectorCollectionInfo> collections,
            Map<String, Object> collectionStates,
            Map<String, Object> maintenance,
            Map<String, Object> deleteOutbox,
            Map<String, Object> mysqlIndexes
    ) {
    }
}
