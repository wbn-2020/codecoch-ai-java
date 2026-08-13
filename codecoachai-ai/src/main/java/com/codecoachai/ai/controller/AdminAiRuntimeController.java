package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.vo.AiRuntimeStatusVO;
import com.codecoachai.ai.service.AiRuntimeStatusService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminAiRuntimeController {

    private static final String PERM_MODEL_LIST = "admin:ai:model:list";

    private final AiRuntimeStatusService runtimeStatusService;
    private final AdminPermissionGuard permissionGuard;

    @GetMapping({"/admin/ai/runtime-status", "/admin/ai/routing-status"})
    public Result<AiRuntimeStatusVO> currentStatus() {
        permissionGuard.require(PERM_MODEL_LIST);
        return Result.success(runtimeStatusService.currentStatus());
    }
}
