package com.codecoachai.search.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.search.constant.IndexNames;
import com.codecoachai.search.controller.SearchRequestGuards.PageWindow;
import com.codecoachai.search.service.IndexManageService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索管理后台：索引重建等运维操作。
 */
@Tag(name = "搜索管理-后台")
@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
@Slf4j
public class AdminSearchController {

    private static final int PARAM_ERROR_CODE = 40000;
    private static final String PERM_QUESTION_SEARCH = "admin:question:list";
    private static final String PERM_RESUME_SEARCH = "admin:search:resume";
    private static final String PERM_INTERVIEW_SEARCH = "admin:search:interview";
    private static final String PERM_INDEX_REBUILD = "admin:search:index:rebuild";
    private static final String REBUILD_CONFIRM_MESSAGE =
            "Dangerous index rebuild requires confirm=true, dryRun=false, reason, and idempotencyKey.";

    private final IndexManageService indexManageService;
    private final ElasticsearchClient esClient;
    private final AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @Operation(summary = "管理员题库全文搜索")
    @GetMapping("/questions")
    public Result<PageResult<JsonNode>> searchQuestions(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String categoryId) throws IOException {
        permissionGuard.require(PERM_QUESTION_SEARCH);
        List<Query> filters = new ArrayList<>();
        if (StringUtils.hasText(difficulty)) {
            filters.add(Query.of(q -> q.term(t -> t.field("difficulty").value(difficulty))));
        }
        if (StringUtils.hasText(categoryId)) {
            filters.add(Query.of(q -> q.term(t -> t.field("categoryId").value(categoryId))));
        }
        return doSearch(IndexNames.QUESTION, keyword, pageNo, pageSize, filters);
    }

    @Operation(summary = "管理员简历全文搜索")
    @GetMapping("/resumes")
    @OperationLog(module = "search", action = "SENSITIVE_RESUME_SEARCH",
            description = "Admin sensitive resume search", logArgs = false, logResponse = false)
    public Result<PageResult<JsonNode>> searchResumes(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Boolean confirmSensitiveAccess,
            @RequestParam(required = false) String accessReason,
            @RequestParam(required = false) Boolean confirm,
            @RequestParam(required = false, defaultValue = "true") Boolean dryRun,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String idempotencyKey) throws IOException {
        permissionGuard.require(PERM_RESUME_SEARCH);
        List<Query> filters = sensitiveUserFilter(userId, scope, confirmSensitiveAccess, "resume");
        String lockKey = requireConfirmedSensitiveSearch("resume", userId, scope,
                confirm, dryRun, effectiveReason(reason, accessReason), idempotencyKey);
        try {
            return doSearch(IndexNames.RESUME, keyword, pageNo, pageSize, filters);
        } catch (RuntimeException | IOException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @Operation(summary = "管理员面试全文搜索")
    @GetMapping("/interviews")
    @OperationLog(module = "search", action = "SENSITIVE_INTERVIEW_SEARCH",
            description = "Admin sensitive interview search", logArgs = false, logResponse = false)
    public Result<PageResult<JsonNode>> searchInterviews(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Boolean confirmSensitiveAccess,
            @RequestParam(required = false) String accessReason,
            @RequestParam(required = false) Boolean confirm,
            @RequestParam(required = false, defaultValue = "true") Boolean dryRun,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String idempotencyKey) throws IOException {
        permissionGuard.require(PERM_INTERVIEW_SEARCH);
        List<Query> filters = sensitiveUserFilter(userId, scope, confirmSensitiveAccess, "interview");
        String lockKey = requireConfirmedSensitiveSearch("interview", userId, scope,
                confirm, dryRun, effectiveReason(reason, accessReason), idempotencyKey);
        try {
            return doSearch(IndexNames.INTERVIEW, keyword, pageNo, pageSize, filters);
        } catch (RuntimeException | IOException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @Operation(summary = "重建指定索引（删除+重建，数据需重新同步）")
    @PostMapping("/indices/{indexName}/rebuild")
    @OperationLog(module = "search", action = "REBUILD_SEARCH_INDEX",
            description = "Rebuild one search index", logArgs = false, logResponse = false)
    public Result<String> rebuildIndex(@PathVariable String indexName,
                                       @RequestParam(defaultValue = "false") boolean confirm,
                                       @RequestParam(defaultValue = "true") boolean dryRun,
                                       @RequestParam(required = false) String reason,
                                       @RequestParam(required = false) String idempotencyKey) throws IOException {
        permissionGuard.require(PERM_INDEX_REBUILD);
        // 重建会删除 ES 索引并等待后续数据同步补齐，管理端必须显式确认，避免误点导致检索短暂无数据。
        if (!confirm) {
            return Result.fail(PARAM_ERROR_CODE, REBUILD_CONFIRM_MESSAGE);
        }
        String lockKey = requireConfirmedRebuild("index:" + indexName, confirm, dryRun, reason, idempotencyKey);
        try {
            indexManageService.rebuild(indexName, true);
        } catch (RuntimeException | IOException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
        return Result.success("索引 " + indexName + " 已重建，请触发数据同步");
    }

    @Operation(summary = "重建所有索引")
    @PostMapping("/indices/rebuild-all")
    @OperationLog(module = "search", action = "REBUILD_ALL_SEARCH_INDEX",
            description = "Rebuild all search indexes", logArgs = false, logResponse = false)
    public Result<String> rebuildAll(@RequestParam(defaultValue = "false") boolean confirm,
                                     @RequestParam(defaultValue = "true") boolean dryRun,
                                     @RequestParam(required = false) String reason,
                                     @RequestParam(required = false) String idempotencyKey) throws IOException {
        permissionGuard.require(PERM_INDEX_REBUILD);
        // 全量重建影响所有搜索域，继续沿用 confirm=true 安全闸。
        if (!confirm) {
            return Result.fail(PARAM_ERROR_CODE, REBUILD_CONFIRM_MESSAGE);
        }
        String lockKey = requireConfirmedRebuild("all", confirm, dryRun, reason, idempotencyKey);
        try {
            indexManageService.rebuildAll(true);
        } catch (RuntimeException | IOException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
        return Result.success("所有索引已重建，请触发数据同步");
    }

    private Result<PageResult<JsonNode>> doSearch(String index, String keyword, Integer pageNo, Integer pageSize,
                                                  List<Query> filters) throws IOException {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        PageWindow pageWindow = SearchRequestGuards.normalizePage(pageNo, pageSize);
        safePageNo = pageWindow.pageNo();
        safePageSize = pageWindow.pageSize();
        int from = pageWindow.from();

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        if (StringUtils.hasText(keyword)) {
            boolBuilder.must(Query.of(q -> q.multiMatch(m -> m
                    .query(keyword)
                    .fields("title^3", "content^2", "tags", "name", "summary", "targetPosition")
                    .fuzziness(SearchRequestGuards.allowsFuzziness(keyword) ? "AUTO" : null)
            )));
        } else {
            boolBuilder.must(Query.of(q -> q.matchAll(ma -> ma)));
        }
        for (Query filter : filters) {
            boolBuilder.filter(filter);
        }

        SearchResponse<JsonNode> response;
        try {
            response = esClient.search(SearchRequest.of(s -> s
                    .index(index)
                    .query(Query.of(q -> q.bool(boolBuilder.build())))
                    .from(pageWindow.from())
                    .size(pageWindow.pageSize())
            ), JsonNode.class);
        } catch (RuntimeException ex) {
            if (!isIndexUnavailable(ex)) {
                throw ex;
            }
            // 索引未初始化时返回空页，避免后台搜索页因 ES 冷启动/迁移滞后直接报错。
            log.warn("ES index unavailable for admin search, return empty page. index={}, reason={}", index, ex.getMessage());
            return Result.success(PageResult.empty(safePageNo, safePageSize));
        }
        long total = response.hits().total() == null ? 0 : response.hits().total().value();
        List<JsonNode> records = new ArrayList<>();
        for (Hit<JsonNode> hit : response.hits().hits()) {
            if (hit.source() != null) {
                records.add(hit.source());
            }
        }
        return Result.success(PageResult.of(records, total, (long) safePageNo, (long) safePageSize));
    }

    private List<Query> userIdFilter(Long userId) {
        if (userId == null) {
            return List.of();
        }
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId must be positive.");
        }
        return List.of(Query.of(q -> q.term(t -> t.field("userId").value(userId))));
    }

    private List<Query> sensitiveUserFilter(Long userId, String scope, Boolean confirmSensitiveAccess,
                                            String dataType) {
        if (userId != null) {
            return userIdFilter(userId);
        }
        if (!"all".equalsIgnoreCase(scope == null ? "" : scope.trim())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Global " + dataType + " search requires scope=all to avoid accidental cross-user access.");
        }
        if (!Boolean.TRUE.equals(confirmSensitiveAccess)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Global " + dataType + " search requires confirmSensitiveAccess=true.");
        }
        return List.of();
    }

    private String requireConfirmedSensitiveSearch(String dataType, Long userId, String scope, Boolean confirm,
                                                  Boolean dryRun, String reason, String idempotencyKey) {
        String target = userId != null ? "user:" + userId : "scope:" + (scope == null ? "unknown" : scope.trim());
        return operationConfirmationGuard.requireConfirmed("search-sensitive:" + dataType + ":" + target,
                confirm, dryRun, reason, idempotencyKey);
    }

    private String effectiveReason(String reason, String accessReason) {
        return StringUtils.hasText(reason) ? reason : accessReason;
    }

    private String requireConfirmedRebuild(String operation, boolean confirm, boolean dryRun, String reason,
                                           String idempotencyKey) {
        return operationConfirmationGuard.requireConfirmed("search-index-rebuild:" + operation,
                confirm, dryRun, reason, idempotencyKey);
    }

    private boolean isIndexUnavailable(RuntimeException ex) {
        String message = ex.getMessage();
        Throwable cause = ex.getCause();
        while ((message == null || message.isBlank()) && cause != null) {
            message = cause.getMessage();
            cause = cause.getCause();
        }
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("index_not_found_exception") || lower.contains("no such index");
    }
}
