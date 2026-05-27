package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
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

    private final QuestionEmbeddingIndexService questionEmbeddingIndexService;

    @PostMapping("/admin/questions/embedding/rebuild")
    public Result<Map<String, Object>> rebuild(@RequestBody(required = false) RebuildDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(questionEmbeddingIndexService.rebuild(dto == null ? null : dto.getLimit()));
    }

    @GetMapping("/questions/similar")
    public Result<List<Map<String, Object>>> similar(@RequestParam Long questionId,
                                                     @RequestParam(required = false) Integer limit) {
        SecurityAssert.requireLoginUserId();
        return Result.success(questionEmbeddingIndexService.searchSimilar(questionId, limit));
    }

    @Data
    public static class RebuildDTO {
        private Integer limit;
    }
}
