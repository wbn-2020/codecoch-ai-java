package com.codecoachai.system.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.system.domain.dto.SystemConfigQueryDTO;
import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
import com.codecoachai.system.domain.dto.SystemConfigStatusDTO;
import com.codecoachai.system.domain.vo.AdminDashboardOverviewVO;
import com.codecoachai.system.domain.vo.AdminSystemOverviewVO;
import com.codecoachai.system.domain.vo.SystemConfigVO;
import com.codecoachai.system.service.SystemConfigService;
import jakarta.validation.Valid;
import java.util.function.Supplier;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SystemConfigController {

    private static final String PERM_CONFIG_LIST = "admin:system:config:list";
    private static final String PERM_CONFIG_WRITE = "admin:system:config:write";
    private static final String PERM_SYSTEM_OVERVIEW = "admin:system:overview";

    private final SystemConfigService systemConfigService;
    private final AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @GetMapping("/admin/configs")
    @OperationLog(module = "system", action = "QUERY_CONFIG", description = "鏌ヨ绯荤粺閰嶇疆", logArgs = false)
    public Result<PageResult<SystemConfigVO>> listConfigs(@Valid SystemConfigQueryDTO query) {
        permissionGuard.require(PERM_CONFIG_LIST);
        return Result.success(systemConfigService.pageConfigs(query));
    }

    @PostMapping("/admin/configs")
    @OperationLog(module = "system", action = "CREATE_CONFIG", description = "鏂板绯荤粺閰嶇疆", logArgs = false)
    public Result<SystemConfigVO> createConfig(@Valid @RequestBody SystemConfigSaveDTO dto) {
        permissionGuard.require(PERM_CONFIG_WRITE);
        return runConfirmedOperation("system-config-create:" + (dto == null ? "new" : dto.getConfigKey()),
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> systemConfigService.createConfig(dto));
    }

    @GetMapping("/admin/configs/{key}")
    @OperationLog(module = "system", action = "GET_CONFIG", description = "鏌ョ湅绯荤粺閰嶇疆璇︽儏", logArgs = false)
    public Result<SystemConfigVO> getConfig(@PathVariable String key) {
        permissionGuard.require(PERM_CONFIG_LIST);
        return Result.success(systemConfigService.getConfig(key));
    }

    @PutMapping("/admin/configs/{id}")
    @OperationLog(module = "system", action = "UPDATE_CONFIG", description = "缂栬緫绯荤粺閰嶇疆", logArgs = false)
    public Result<SystemConfigVO> updateConfigById(@PathVariable Long id, @RequestBody SystemConfigSaveDTO dto) {
        permissionGuard.require(PERM_CONFIG_WRITE);
        return runConfirmedOperation("system-config-update:id:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> systemConfigService.updateConfigById(id, dto));
    }

    @PutMapping({"/admin/configs/keys/{configKey}", "/admin/configs/key/{configKey}"})
    @OperationLog(module = "system", action = "UPDATE_CONFIG_BY_KEY", description = "Update system config by key", logArgs = false)
    public Result<SystemConfigVO> updateConfigByKey(@PathVariable String configKey, @RequestBody SystemConfigSaveDTO dto) {
        permissionGuard.require(PERM_CONFIG_WRITE);
        return runConfirmedOperation("system-config-update:key:" + configKey,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> systemConfigService.updateConfigByKey(configKey, dto));
    }

    @PutMapping("/admin/configs/{key}/status")
    @OperationLog(module = "system", action = "UPDATE_CONFIG_STATUS", description = "鍒囨崲绯荤粺閰嶇疆鐘舵€?", logArgs = false)
    public Result<SystemConfigVO> updateConfigStatus(@PathVariable String key,
                                                     @Valid @RequestBody SystemConfigStatusDTO dto) {
        permissionGuard.require(PERM_CONFIG_WRITE);
        return runConfirmedOperation("system-config-status:" + key,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> systemConfigService.updateConfigStatus(key, dto));
    }

    @DeleteMapping("/admin/configs/{id}")
    @OperationLog(module = "system", action = "DELETE_CONFIG", description = "鍒犻櫎绯荤粺閰嶇疆")
    public Result<Void> deleteConfig(@PathVariable String id,
                                     @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        permissionGuard.require(PERM_CONFIG_WRITE);
        return runConfirmedVoidOperation("system-config-delete:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> systemConfigService.deleteConfig(id));
    }

    private <T> Result<T> runConfirmedOperation(String operation, Boolean confirm, Boolean dryRun,
                                                String reason, String idempotencyKey, Supplier<T> action) {
        String lockKey = operationConfirmationGuard.requireConfirmed(operation, confirm, dryRun, reason, idempotencyKey);
        try {
            return Result.success(action.get());
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    private Result<Void> runConfirmedVoidOperation(String operation, Boolean confirm, Boolean dryRun,
                                                   String reason, String idempotencyKey, Runnable action) {
        String lockKey = operationConfirmationGuard.requireConfirmed(operation, confirm, dryRun, reason, idempotencyKey);
        try {
            action.run();
            return Result.success();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @Data
    public static class AdminOperationConfirmDTO {
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }

    @GetMapping("/admin/system/overview")
    @OperationLog(module = "system", action = "QUERY_SYSTEM_OVERVIEW", description = "鏌ヨ绯荤粺姒傝", logArgs = false)
    public Result<AdminSystemOverviewVO> overview() {
        permissionGuard.require(PERM_SYSTEM_OVERVIEW);
        return Result.success(systemConfigService.overview());
    }

    @GetMapping("/admin/dashboard/overview")
    @OperationLog(module = "system", action = "QUERY_DASHBOARD", description = "鏌ヨ绠＄悊棣栭〉姒傝", logArgs = false)
    public Result<AdminDashboardOverviewVO> dashboardOverview() {
        permissionGuard.require(PERM_SYSTEM_OVERVIEW);
        return Result.success(systemConfigService.dashboardOverview());
    }
}
