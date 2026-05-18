package com.codecoachai.task.controller;

import com.codecoachai.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异步任务后台管理 Controller（骨架，后续补全分页、详情、重试、死信处理等接口）。
 */
@Tag(name = "任务管理-后台")
@RestController
@RequestMapping("/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("task-service ok");
    }
}
