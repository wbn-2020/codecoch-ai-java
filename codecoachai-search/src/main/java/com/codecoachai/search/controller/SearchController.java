package com.codecoachai.search.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.search.constant.IndexNames;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一搜索 Controller。
 * 前端通过 Gateway 路由 /search/** 到本服务。
 */
@Slf4j
@Tag(name = "搜索")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ElasticsearchClient esClient;

    @Operation(summary = "Unified search entry")
    @GetMapping
    public Result<PageResult<JsonNode>> search(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String reportStatus) throws IOException {
        String normalizedType = StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "questions";
        switch (normalizedType) {
            case "questions":
            case "question":
                return searchQuestions(keyword, pageNo, pageSize, difficulty, categoryId);
            case "resumes":
            case "resume":
                return searchResumes(keyword, pageNo, pageSize);
            case "interviews":
            case "interview":
                return searchInterviews(keyword, pageNo, pageSize);
            case "reports":
            case "report":
                return searchReports(keyword, pageNo, pageSize, reportStatus);
            default:
                return unsupportedSearchType(type);
        }
    }

    @Operation(summary = "题库全文搜索")
    @GetMapping("/questions")
    public Result<PageResult<JsonNode>> searchQuestions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String categoryId) throws IOException {
        return doSearch(IndexNames.QUESTION, keyword, pageNo, pageSize,
                buildQuestionFilters(difficulty, categoryId));
    }

    @Operation(summary = "当前用户简历搜索")
    @GetMapping("/resumes")
    public Result<PageResult<JsonNode>> searchResumes(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) throws IOException {
        Long userId = SecurityAssert.requireLoginUserId();
        return doSearch(IndexNames.RESUME, keyword, pageNo, pageSize, userScopedFilters(userId));
    }

    @Operation(summary = "面试历史搜索")
    @GetMapping("/interviews")
    public Result<PageResult<JsonNode>> searchInterviews(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) throws IOException {
        Long userId = SecurityAssert.requireLoginUserId();
        return doSearch(IndexNames.INTERVIEW, keyword, pageNo, pageSize, userScopedFilters(userId));
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("search-service ok");
    }

    @Operation(summary = "Reports search compatibility")
    @GetMapping("/reports")
    public Result<PageResult<JsonNode>> searchReports(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String reportStatus) throws IOException {
        Long userId = SecurityAssert.requireLoginUserId();
        return doSearch(IndexNames.INTERVIEW, keyword, pageNo, pageSize,
                reportFilters(userId, reportStatus));
    }

    // ==================== 内部方法 ====================

    private Result<PageResult<JsonNode>> doSearch(String index, String keyword,
                                                   Integer pageNo, Integer pageSize,
                                                   List<Query> filters) throws IOException {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int from = (safePageNo - 1) * safePageSize;

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        if (StringUtils.hasText(keyword)) {
            boolBuilder.must(Query.of(q -> q.multiMatch(m -> m
                    .query(keyword)
                    .fields("title^3", "content^2", "tags", "name", "summary^2", "targetPosition",
                            "weakPoints", "strengths", "mainProblems", "projectProblems",
                            "reviewSuggestions", "suggestions", "reportContent")
                    .fuzziness("AUTO")
            )));
        } else {
            boolBuilder.must(Query.of(q -> q.matchAll(ma -> ma)));
        }
        for (Query filter : filters) {
            boolBuilder.filter(filter);
        }

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(index)
                .query(Query.of(q -> q.bool(boolBuilder.build())))
                .from(from)
                .size(safePageSize)
        );

        SearchResponse<JsonNode> response;
        try {
            response = esClient.search(searchRequest, JsonNode.class);
        } catch (RuntimeException ex) {
            if (!isIndexUnavailable(ex)) {
                throw ex;
            }
            log.warn("ES index unavailable for search, return empty page. index={}, reason={}", index, ex.getMessage());
            return Result.success(PageResult.empty(safePageNo, safePageSize));
        }
        long total = response.hits().total() != null ? response.hits().total().value() : 0;
        List<JsonNode> records = new ArrayList<>();
        for (Hit<JsonNode> hit : response.hits().hits()) {
            if (hit.source() != null) {
                records.add(hit.source());
            }
        }

        return Result.success(PageResult.of(records, total, (long) safePageNo, (long) safePageSize));
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

    private Result<PageResult<JsonNode>> unsupportedSearchType(String type) {
        return Result.fail(40000, "暂不支持的搜索类型：" + (type == null ? "" : type));
    }

    private List<Query> buildQuestionFilters(String difficulty, String categoryId) {
        List<Query> filters = new ArrayList<>();
        if (StringUtils.hasText(difficulty)) {
            filters.add(Query.of(q -> q.term(t -> t.field("difficulty").value(difficulty))));
        }
        if (StringUtils.hasText(categoryId)) {
            filters.add(Query.of(q -> q.term(t -> t.field("categoryId").value(categoryId))));
        }
        return filters;
    }

    private List<Query> userScopedFilters(Long userId) {
        return List.of(Query.of(q -> q.term(t -> t.field("userId").value(String.valueOf(userId)))));
    }

    private List<Query> reportFilters(Long userId, String reportStatus) {
        List<Query> filters = new ArrayList<>(userScopedFilters(userId));
        if (StringUtils.hasText(reportStatus)) {
            filters.add(Query.of(q -> q.term(t -> t.field("reportStatus").value(reportStatus))));
        }
        return filters;
    }
}
