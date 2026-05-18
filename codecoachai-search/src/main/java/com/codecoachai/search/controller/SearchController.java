package com.codecoachai.search.controller;

import com.codecoachai.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索 Controller 骨架（题库/简历/面试三类后续补全）。
 */
@Tag(name = "搜索")
@RestController
@RequestMapping("/search")
public class SearchController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("search-service ok");
    }
}
