package com.codecoachai.system.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
import com.codecoachai.system.domain.dto.SystemConfigStatusDTO;
import com.codecoachai.system.domain.vo.AdminDashboardOverviewVO;
import com.codecoachai.system.domain.vo.AdminSystemOverviewVO;
import com.codecoachai.system.domain.vo.SystemConfigVO;
import com.codecoachai.system.service.SystemConfigService;
import jakarta.validation.Valid;
import java.util.List;
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

    @GetMapping("/admin/configs")
    @OperationLog(module = "system", action = "QUERY_CONFIG", description = "查询系统配置", logArgs = false)
    public Result<List<SystemConfigVO>> listConfigs() {
        permissionGuard.require(PERM_CONFIG_LIST);
        return Result.success(systemConfigService.listConfigs());
    }

    @PostMapping("/admin/configs")
    @OperationLog(module = "system", action = "CREATE_CONFIG", description = "新增系统配置", logArgs = false)
    public Result<SystemConfigVO> createConfig(@Valid @RequestBody SystemConfigSaveDTO dto) {
        permissionGuard.require(PERM_CONFIG_WRITE);
        return Result.success(systemConfigService.createConfig(dto));
    }

    @GetMapping("/admin/configs/{key}")
    @OperationLog(module = "system", action = "GET_CONFIG", description = "查看系统配置详情", logArgs = false)
    public Result<SystemConfigVO> getConfig(@PathVariable String key) {
        permissionGuard.require(PERM_CONFIG_LIST);
        return Result.success(systemConfigService.getConfig(key));
    }

    @PutMapping("/admin/configs/{key}")
    @OperationLog(module = "system", action = "UPDATE_CONFIG", description = "编辑系统配置", logArgs = false)
    public Result<SystemConfigVO> updateConfig(@PathVariable String key, @RequestBody SystemConfigSaveDTO dto) {
        permissionGuard.require(PERM_CONFIG_WRITE);
        return Result.success(systemConfigService.updateConfig(key, dto));
    }

    @PutMapping("/admin/configs/{key}/status")
    @OperationLog(module = "system", action = "UPDATE_CONFIG_STATUS", description = "切换系统配置状态")
    public Result<SystemConfigVO> updateConfigStatus(@PathVariable String key,
                                                     @Valid @RequestBody SystemConfigStatusDTO dto) {
        permissionGuard.require(PERM_CONFIG_WRITE);
        return Result.success(systemConfigService.updateConfigStatus(key, dto));
    }

    @DeleteMapping("/admin/configs/{id}")
    @OperationLog(module = "system", action = "DELETE_CONFIG", description = "删除系统配置")
    public Result<Void> deleteConfig(@PathVariable String id) {
        permissionGuard.require(PERM_CONFIG_WRITE);
        systemConfigService.deleteConfig(id);
        return Result.success();
    }

    @GetMapping("/admin/system/overview")
    @OperationLog(module = "system", action = "QUERY_SYSTEM_OVERVIEW", description = "查询系统概览", logArgs = false)
    public Result<AdminSystemOverviewVO> overview() {
        permissionGuard.require(PERM_SYSTEM_OVERVIEW);
        return Result.success(systemConfigService.overview());
    }

    @GetMapping("/admin/dashboard/overview")
    @OperationLog(module = "system", action = "QUERY_DASHBOARD", description = "查询管理首页概览", logArgs = false)
    public Result<AdminDashboardOverviewVO> dashboardOverview() {
        permissionGuard.require(PERM_SYSTEM_OVERVIEW);
        return Result.success(systemConfigService.dashboardOverview());
    }
}
