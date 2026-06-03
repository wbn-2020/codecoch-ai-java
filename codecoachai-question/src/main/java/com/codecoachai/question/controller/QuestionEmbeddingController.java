package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.common.vector.service.VectorIndexJobService;
import com.codecoachai.question.config.QuestionDuplicateProperties;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuestionEmbeddingController {

    private static final String PERM_QUESTION_LIST = "admin:question:list";
    private static final String PERM_QUESTION_DEDUPE = "admin:question:dedupe";
    private static final String PERM_QUESTION_EMBEDDING_REBUILD = "admin:question:embedding:rebuild";

    private final QuestionEmbeddingIndexService questionEmbeddingIndexService;
    private final QuestionDuplicateProperties questionDuplicateProperties;
    private final VectorIndexJobService vectorIndexJobService;
    private final AdminPermissionGuard adminPermissionGuard;

    @OperationLog(module = "question", action = "REBUILD_QUESTION_EMBEDDING", description = "重建题目向量", logArgs = false, logResponse = false)
    @PostMapping("/admin/questions/embedding/rebuild")
    public Result<Map<String, Object>> rebuild(@RequestBody(required = false) RebuildDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_EMBEDDING_REBUILD);
        Integer limit = dto == null ? null : dto.getLimit();
        Long jobId = vectorIndexJobService.start("QUESTION_REBUILD", "QUESTION", null, limit);
        try {
            Map<String, Object> result = new LinkedHashMap<>(questionEmbeddingIndexService.rebuild(limit));
            finishQuestionVectorJob(jobId, result);
            vectorIndexJobService.attach(result, jobId);
            return Result.success(result);
        } catch (Exception ex) {
            vectorIndexJobService.fail(jobId, ex);
            throw ex;
        }
    }

    @GetMapping("/admin/questions/embedding/stats")
    public Result<Map<String, Object>> stats() {
        adminPermissionGuard.require(PERM_QUESTION_LIST);
        return Result.success(questionEmbeddingIndexService.stats());
    }

    @OperationLog(module = "question", action = "RETRY_QUESTION_EMBEDDING", description = "重试失败题目向量", logArgs = false, logResponse = false)
    @PostMapping("/admin/questions/embedding/retry-failed")
    public Result<Map<String, Object>> retryFailed(@RequestBody(required = false) RebuildDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_EMBEDDING_REBUILD);
        Integer limit = dto == null ? null : dto.getLimit();
        Long jobId = vectorIndexJobService.start("QUESTION_RETRY", "QUESTION", "FAILED_OR_STALE", limit);
        try {
            Map<String, Object> result = new LinkedHashMap<>(questionEmbeddingIndexService.retryFailed(limit));
            finishQuestionVectorJob(jobId, result);
            vectorIndexJobService.attach(result, jobId);
            return Result.success(result);
        } catch (Exception ex) {
            vectorIndexJobService.fail(jobId, ex);
            throw ex;
        }
    }

    @GetMapping("/questions/similar")
    public Result<List<Map<String, Object>>> similar(@RequestParam Long questionId,
                                                     @RequestParam(required = false) Integer limit) {
        SecurityAssert.requireLoginUserId();
        return Result.success(questionEmbeddingIndexService.searchSimilar(questionId, limit));
    }

    @GetMapping("/admin/questions/duplicate/config")
    public Result<QuestionDuplicateProperties> duplicateConfig() {
        adminPermissionGuard.require(PERM_QUESTION_DEDUPE);
        return Result.success(questionDuplicateProperties);
    }

    @Data
    public static class RebuildDTO {
        private Integer limit;
    }

    private void finishQuestionVectorJob(Long jobId, Map<String, Object> result) {
        long total = firstPositive(numberValue(result.get("updated")), numberValue(result.get("matched")));
        long success = firstPositive(numberValue(result.get("vectorUpdated")), numberValue(result.get("retried")));
        long vectorDeleted = numberValue(result.get("vectorDeleted"));
        long failed = numberValue(result.get("failedBatches")) + errorCount(result.get("errors"));
        String status = failed > 0 ? "FAILED" : "SUCCESS";
        String error = firstError(result.get("errors"));
        vectorIndexJobService.finish(jobId, status, total, success, failed,
                numberValue(result.get("vectorUpdated")), vectorDeleted, error);
    }

    private long firstPositive(long first, long second) {
        return first > 0 ? first : second;
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private long errorCount(Object value) {
        return value instanceof List<?> list ? list.size() : 0L;
    }

    private String firstError(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            return first == null ? null : String.valueOf(first);
        }
        return null;
    }

}
