package com.codecoachai.system.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
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

    private final SystemConfigService systemConfigService;

    @GetMapping("/admin/configs")
    public Result<List<SystemConfigVO>> listConfigs() {
        SecurityAssert.requireAdmin();
        return Result.success(systemConfigService.listConfigs());
    }

    @PostMapping("/admin/configs")
    public Result<SystemConfigVO> createConfig(@Valid @RequestBody SystemConfigSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(systemConfigService.createConfig(dto));
    }

    @PutMapping("/admin/configs/{id}")
    public Result<SystemConfigVO> updateConfig(@PathVariable Long id, @Valid @RequestBody SystemConfigSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(systemConfigService.updateConfig(id, dto));
    }

    @DeleteMapping("/admin/configs/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        systemConfigService.deleteConfig(id);
        return Result.success();
    }

    @GetMapping("/admin/system/overview")
    public Result<AdminSystemOverviewVO> overview() {
        SecurityAssert.requireAdmin();
        return Result.success(systemConfigService.overview());
    }

    @GetMapping("/admin/dashboard/overview")
    public Result<AdminDashboardOverviewVO> dashboardOverview() {
        SecurityAssert.requireAdmin();
        return Result.success(systemConfigService.dashboardOverview());
    }
}
