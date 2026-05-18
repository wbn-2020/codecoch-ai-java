package com.codecoachai.search.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.search.constant.IndexNames;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    @Operation(summary = "题库全文搜索")
    @GetMapping("/questions")
    public Result<PageResult<JsonNode>> searchQuestions(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String categoryId) throws IOException {
        return doSearch(IndexNames.QUESTION, keyword, pageNo, pageSize,
                buildQuestionFilters(difficulty, categoryId));
    }

    @Operation(summary = "简历搜索（管理端）")
    @GetMapping("/resumes")
    public Result<PageResult<JsonNode>> searchResumes(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) throws IOException {
        return doSearch(IndexNames.RESUME, keyword, pageNo, pageSize, List.of());
    }

    @Operation(summary = "面试历史搜索")
    @GetMapping("/interviews")
    public Result<PageResult<JsonNode>> searchInterviews(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long userId) throws IOException {
        List<Query> filters = new ArrayList<>();
        if (userId != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("userId").value(userId))));
        }
        return doSearch(IndexNames.INTERVIEW, keyword, pageNo, pageSize, filters);
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("search-service ok");
    }

    // ==================== 内部方法 ====================

    private Result<PageResult<JsonNode>> doSearch(String index, String keyword,
                                                   Integer pageNo, Integer pageSize,
                                                   List<Query> filters) throws IOException {
        int from = (pageNo - 1) * pageSize;

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        if (StringUtils.hasText(keyword)) {
            boolBuilder.must(Query.of(q -> q.multiMatch(m -> m
                    .query(keyword)
                    .fields("title^3", "content^2", "tags", "name", "summary", "targetPosition")
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
                .size(pageSize)
        );

        SearchResponse<JsonNode> response = esClient.search(searchRequest, JsonNode.class);
        long total = response.hits().total() != null ? response.hits().total().value() : 0;
        List<JsonNode> records = new ArrayList<>();
        for (Hit<JsonNode> hit : response.hits().hits()) {
            if (hit.source() != null) {
                records.add(hit.source());
            }
        }

        return Result.success(PageResult.of(records, total, (long) pageNo, (long) pageSize));
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
}
