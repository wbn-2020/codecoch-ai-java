package com.codecoachai.search.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.search.service.IndexManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Operation(summary = "重建指定索引（删除+重建，数据需重新同步）")
    @PostMapping("/indices/{indexName}/rebuild")
    public Result<String> rebuildIndex(@PathVariable String indexName) throws IOException {
        indexManageService.rebuild(indexName);
        return Result.success("索引 " + indexName + " 已重建，请触发数据同步");
    }

    @Operation(summary = "重建所有索引")
    @PostMapping("/indices/rebuild-all")
    public Result<String> rebuildAll() throws IOException {
        indexManageService.rebuildAll();
        return Result.success("所有索引已重建，请触发数据同步");
    }
}
