package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.config.KnowledgeProperties;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeVectorRebuildVO;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.vector.config.VectorStoreProperties;
import com.codecoachai.common.vector.domain.VectorCollectionInfo;
import com.codecoachai.common.vector.service.VectorIndexJobService;
import com.codecoachai.common.vector.service.VectorStoreClient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping({"/admin/vector-store", "/admin/analytics/vector-store"})
public class AdminVectorStoreController {

    private static final String QUESTION_COLLECTION = "question_embedding";
    private static final String VECTOR_OPS_PERMISSION = "admin:analytics:ai";

    private final VectorStoreClient vectorStoreClient;
    private final VectorIndexJobService vectorIndexJobService;
    private final V4AdminPermissionGuard permissionGuard;
    private final JdbcTemplate jdbcTemplate;
    private final AgentV4OpsService agentV4OpsService;
    private final KnowledgeProperties knowledgeProperties;
    private final VectorStoreProperties vectorStoreProperties;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        permissionGuard.require(VECTOR_OPS_PERMISSION);
        List<VectorCollectionInfo> collections = coreCollections().stream()
                .map(vectorStoreClient::collectionInfo)
                .toList();
        Map<String, Object> deleteOutbox = vectorDeleteOutboxStats();
        boolean collectionsPresent = collections.stream().allMatch(item -> Boolean.TRUE.equals(item.getExists()));
        boolean dimensionMatched = collections.stream()
                .filter(item -> Boolean.TRUE.equals(item.getExists()))
                .map(VectorCollectionInfo::getVectorSize)
                .filter(size -> size != null && size > 0)
                .distinct()
                .count() <= 1 && collectionsPresent;
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("enabled", vectorStoreClient.isEnabled());
        checks.put("collectionsPresent", collectionsPresent);
        checks.put("dimensionMatched", dimensionMatched);
        checks.put("deleteOutboxClear", Boolean.TRUE.equals(deleteOutbox.get("clear")));
        return Result.success(Map.of(
                "enabled", vectorStoreClient.isEnabled(),
                "checks", checks,
                "config", vectorRuntimeConfig(),
                "collections", collections,
                "deleteOutbox", deleteOutbox,
                "embeddingMetrics", embeddingMetrics(),
                "mysqlIndexes", mysqlVectorIndexStats()
        ));
    }

    @GetMapping("/failures")
    public Result<Map<String, Object>> failures(@RequestParam(required = false) String type,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Integer limit) {
        permissionGuard.require(VECTOR_OPS_PERMISSION);
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
    public Result<Map<String, Object>> retryVectorDeletes(@RequestParam(required = false) Integer limit) {
        permissionGuard.require(VECTOR_OPS_PERMISSION);
        Long jobId = vectorIndexJobService.start("DELETE_OUTBOX_RETRY", "DELETE_OUTBOX", null, limit);
        try {
            Map<String, Object> result = new LinkedHashMap<>(retryVectorDeletesInternal(limit));
            vectorIndexJobService.finish(jobId, "SUCCESS", result,
                    numberValue(result.get("matched")), numberValue(result.get("deleted")), numberValue(result.get("failed")),
                    numberValue(result.get("deleted")), 0L, null);
            vectorIndexJobService.attach(result, jobId);
            return Result.success(result);
        } catch (Exception ex) {
            vectorIndexJobService.fail(jobId, ex);
            throw ex;
        }
    }

    @PostMapping("/knowledge/rebuild")
    public Result<KnowledgeVectorRebuildVO> rebuildKnowledgeVectors(@RequestParam(required = false) Integer limit) {
        permissionGuard.require(VECTOR_OPS_PERMISSION);
        Long jobId = vectorIndexJobService.start("KNOWLEDGE_REBUILD", "KNOWLEDGE", null, limit);
        try {
            KnowledgeVectorRebuildVO result = agentV4OpsService.rebuildAllKnowledgeVectors(limit);
            finishKnowledgeVectorJob(jobId, result);
            return Result.success(result);
        } catch (Exception ex) {
            vectorIndexJobService.fail(jobId, ex);
            throw ex;
        }
    }

    @PostMapping("/knowledge/retry-failed")
    public Result<KnowledgeVectorRebuildVO> retryFailedKnowledgeVectors(@RequestParam(required = false) Integer limit) {
        permissionGuard.require(VECTOR_OPS_PERMISSION);
        Long jobId = vectorIndexJobService.start("KNOWLEDGE_RETRY", "KNOWLEDGE", "FAILED_OR_STALE", limit);
        try {
            KnowledgeVectorRebuildVO result = agentV4OpsService.retryAllFailedKnowledgeVectors(limit);
            finishKnowledgeVectorJob(jobId, result);
            return Result.success(result);
        } catch (Exception ex) {
            vectorIndexJobService.fail(jobId, ex);
            throw ex;
        }
    }

    @GetMapping("/jobs")
    public Result<PageResult<Map<String, Object>>> jobs(@RequestParam(required = false) String jobType,
                                                        @RequestParam(required = false) String scopeType,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) Long pageNo,
                                                        @RequestParam(required = false) Long pageSize) {
        permissionGuard.require(VECTOR_OPS_PERMISSION);
        return Result.success(vectorIndexJobService.page(jobType, scopeType, status, pageNo, pageSize));
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
        config.put("baseUrl", vectorStoreProperties.getBaseUrl());
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
                    + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
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
        try {
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM " + tableName + " WHERE deleted = 0", Long.class);
            List<Map<String, Object>> statusCounts = jdbcTemplate.queryForList(
                    "SELECT COALESCE(index_status, 'PENDING') AS status, COUNT(1) AS count "
                            + "FROM " + tableName + " WHERE deleted = 0 "
                            + "GROUP BY COALESCE(index_status, 'PENDING') ORDER BY status");
            List<Map<String, Object>> modelCounts = jdbcTemplate.queryForList(
                    "SELECT COALESCE(embedding_model, 'UNKNOWN') AS model, COUNT(1) AS count "
                            + "FROM " + tableName + " WHERE deleted = 0 "
                            + "GROUP BY COALESCE(embedding_model, 'UNKNOWN') ORDER BY count DESC, model LIMIT 8");
            String lastIndexedAt = jdbcTemplate.queryForObject(
                    "SELECT MAX(indexed_at) FROM " + tableName + " WHERE deleted = 0", String.class);
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
            stats.put("errorMessage", firstText(ex.getMessage(), ex.getClass().getSimpleName()));
        }
        return stats;
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
                    + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
        }
        return stats;
    }

    private List<Map<String, Object>> questionVectorFailures(List<String> statuses, int limit, List<String> errors) {
        try {
            List<Object> args = new ArrayList<>(statuses);
            args.add(limit);
            return jdbcTemplate.queryForList("""
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
        } catch (DataAccessException ex) {
            errors.add("question_embedding query failed: " + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
            return List.of();
        }
    }

    private List<Map<String, Object>> knowledgeVectorFailures(List<String> statuses, int limit, List<String> errors) {
        try {
            List<Object> args = new ArrayList<>(statuses);
            args.add(limit);
            return jdbcTemplate.queryForList("""
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
        } catch (DataAccessException ex) {
            errors.add("personal_knowledge_chunk query failed: "
                    + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
            return List.of();
        }
    }

    private List<Map<String, Object>> deleteOutboxFailures(List<String> statuses, int limit, List<String> errors) {
        try {
            List<Object> args = new ArrayList<>(statuses);
            args.add(limit);
            return jdbcTemplate.queryForList("""
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
        } catch (DataAccessException ex) {
            errors.add("vector_delete_outbox query failed: "
                    + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
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
                    + firstText(ex.getMessage(), ex.getClass().getSimpleName())));
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
                errors.add(collectionName + ": " + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
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
                        firstText(ex == null ? null : ex.getMessage(), "unknown error")).toArray());
    }

    private void finishKnowledgeVectorJob(Long jobId, KnowledgeVectorRebuildVO result) {
        long total = numberValue(result == null ? null : result.getChunkCount());
        long success = numberValue(result == null ? null : result.getVectorUpdated());
        long failed = result == null || result.getFailedDocuments() == null ? 0L : result.getFailedDocuments().size();
        long updated = numberValue(result == null ? null : result.getVectorUpdated());
        long deleted = numberValue(result == null ? null : result.getVectorDeleted());
        String error = result == null || result.getErrors() == null || result.getErrors().isEmpty()
                ? null : String.join("; ", result.getErrors().stream().limit(5).toList());
        vectorIndexJobService.finish(jobId, failed > 0 || error != null ? "FAILED" : "SUCCESS",
                Map.of(), total, success, failed, updated, deleted, error);
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
}
