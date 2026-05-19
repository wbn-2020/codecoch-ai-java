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
import com.codecoachai.search.service.IndexManageService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
public class AdminSearchController {

    private final IndexManageService indexManageService;
    private final ElasticsearchClient esClient;

    @Operation(summary = "管理员题库全文搜索")
    @GetMapping("/questions")
    public Result<PageResult<JsonNode>> searchQuestions(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String categoryId) throws IOException {
        SecurityAssert.requireAdmin();
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
    public Result<PageResult<JsonNode>> searchResumes(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long userId) throws IOException {
        SecurityAssert.requireAdmin();
        return doSearch(IndexNames.RESUME, keyword, pageNo, pageSize, userIdFilter(userId));
    }

    @Operation(summary = "管理员面试全文搜索")
    @GetMapping("/interviews")
    public Result<PageResult<JsonNode>> searchInterviews(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long userId) throws IOException {
        SecurityAssert.requireAdmin();
        return doSearch(IndexNames.INTERVIEW, keyword, pageNo, pageSize, userIdFilter(userId));
    }

    @Operation(summary = "重建指定索引（删除+重建，数据需重新同步）")
    @PostMapping("/indices/{indexName}/rebuild")
    public Result<String> rebuildIndex(@PathVariable String indexName) throws IOException {
        SecurityAssert.requireAdmin();
        indexManageService.rebuild(indexName);
        return Result.success("索引 " + indexName + " 已重建，请触发数据同步");
    }

    @Operation(summary = "重建所有索引")
    @PostMapping("/indices/rebuild-all")
    public Result<String> rebuildAll() throws IOException {
        SecurityAssert.requireAdmin();
        indexManageService.rebuildAll();
        return Result.success("所有索引已重建，请触发数据同步");
    }

    private Result<PageResult<JsonNode>> doSearch(String index, String keyword, Integer pageNo, Integer pageSize,
                                                  List<Query> filters) throws IOException {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int from = (safePageNo - 1) * safePageSize;

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

        SearchResponse<JsonNode> response = esClient.search(SearchRequest.of(s -> s
                .index(index)
                .query(Query.of(q -> q.bool(boolBuilder.build())))
                .from(from)
                .size(safePageSize)
        ), JsonNode.class);
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
        return List.of(Query.of(q -> q.term(t -> t.field("userId").value(userId))));
    }
}
