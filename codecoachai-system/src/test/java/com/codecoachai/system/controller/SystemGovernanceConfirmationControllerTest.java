package com.codecoachai.system.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionCache;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.system.domain.dto.RoleMenuAssignDTO;
import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
import com.codecoachai.system.domain.entity.SysMenu;
import com.codecoachai.system.domain.entity.SysRoleMenu;
import com.codecoachai.system.mapper.SysMenuMapper;
import com.codecoachai.system.mapper.SysRoleMenuMapper;
import com.codecoachai.system.service.SystemConfigService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemGovernanceConfirmationControllerTest {

    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private SysRoleMenuMapper roleMenuMapper;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminPermissionCache adminPermissionCache;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private SystemConfigController systemConfigController;
    private AdminMenuController adminMenuController;

    @BeforeEach
    void setUp() {
        systemConfigController = new SystemConfigController(
                systemConfigService,
                permissionGuard,
                operationConfirmationGuard);
        adminMenuController = new AdminMenuController(
                menuMapper,
                roleMenuMapper,
                permissionGuard,
                adminPermissionCache,
                operationConfirmationGuard);
    }

    @Test
    void createConfigForwardsDryRunToConfirmationGuard() {
        SystemConfigSaveDTO dto = configDto(false);

        systemConfigController.createConfig(dto);

        verify(permissionGuard).require("admin:system:config:write");
        verify(operationConfirmationGuard).requireConfirmed(
                "system-config-create:feature.flag",
                true,
                false,
                "confirm system config change",
                "system-config-create-1234");
        verify(systemConfigService).createConfig(dto);
    }

    @Test
    void deleteConfigRejectsDryRunBeforeMutatingService() {
        SystemConfigController.AdminOperationConfirmDTO dto = new SystemConfigController.AdminOperationConfirmDTO();
        dto.setConfirm(true);
        dto.setDryRun(true);
        dto.setReason("preview config delete");
        dto.setIdempotencyKey("system-config-delete-1234");
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR, "dryRun rejected"))
                .when(operationConfirmationGuard)
                .requireConfirmed(
                        eq("system-config-delete:feature.flag"),
                        eq(true),
                        eq(true),
                        eq("preview config delete"),
                        eq("system-config-delete-1234"));

        assertThrows(BusinessException.class,
                () -> systemConfigController.deleteConfig("feature.flag", dto));

        verify(systemConfigService, never()).deleteConfig(any());
    }

    @Test
    void updateConfigByKeyReleasesConfirmationLockWhenServiceFails() {
        SystemConfigSaveDTO dto = configDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                eq("system-config-update:key:feature.flag"),
                eq(true),
                eq(false),
                eq("confirm system config change"),
                eq("system-config-create-1234"))).thenReturn("system-config-lock");
        doThrow(new IllegalStateException("config update failed"))
                .when(systemConfigService).updateConfigByKey("feature.flag", dto);

        assertThrows(IllegalStateException.class,
                () -> systemConfigController.updateConfigByKey("feature.flag", dto));

        verify(operationConfirmationGuard).release("system-config-lock");
    }

    @Test
    void assignRoleMenusForwardsDryRunToConfirmationGuard() {
        RoleMenuAssignDTO dto = roleMenuAssignDto(false);
        when(menuMapper.selectList(any())).thenReturn(List.of(menu(10L), menu(11L)));

        adminMenuController.assignRoleMenus(3L, dto);

        verify(permissionGuard).require("admin:role:assign");
        verify(operationConfirmationGuard).requireConfirmed(
                "role-menu-assign:3",
                true,
                false,
                "confirm role menu grant",
                "role-menu-grant-1234");
        verify(roleMenuMapper).delete(any());
        verify(roleMenuMapper, times(2)).insert(any(SysRoleMenu.class));
        verify(adminPermissionCache).invalidateUsersByRoleId(3L);
    }

    @Test
    void assignRoleMenusReleasesConfirmationLockWhenMapperFails() {
        RoleMenuAssignDTO dto = roleMenuAssignDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                eq("role-menu-assign:3"),
                eq(true),
                eq(false),
                eq("confirm role menu grant"),
                eq("role-menu-grant-1234"))).thenReturn("role-menu-lock");
        when(menuMapper.selectList(any())).thenReturn(List.of(menu(10L), menu(11L)));
        when(roleMenuMapper.insert(any(SysRoleMenu.class)))
                .thenThrow(new IllegalStateException("grant failed"));

        assertThrows(IllegalStateException.class, () -> adminMenuController.assignRoleMenus(3L, dto));

        verify(operationConfirmationGuard).release("role-menu-lock");
        verify(adminPermissionCache, never()).invalidateUsersByRoleId(3L);
    }

    @Test
    void menuDeleteRejectsDryRunBeforeMapperMutation() {
        AdminMenuController.AdminOperationConfirmDTO dto = new AdminMenuController.AdminOperationConfirmDTO();
        dto.setConfirm(true);
        dto.setDryRun(true);
        dto.setReason("preview menu delete");
        dto.setIdempotencyKey("menu-delete-1234");
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR, "dryRun rejected"))
                .when(operationConfirmationGuard)
                .requireConfirmed(
                        eq("menu-delete:5"),
                        eq(true),
                        eq(true),
                        eq("preview menu delete"),
                        eq("menu-delete-1234"));

        assertThrows(BusinessException.class, () -> adminMenuController.delete(5L, dto));

        verify(roleMenuMapper, never()).delete(any());
        verify(menuMapper, never()).deleteById(any(Long.class));
    }

    private static SystemConfigSaveDTO configDto(Boolean dryRun) {
        SystemConfigSaveDTO dto = new SystemConfigSaveDTO();
        dto.setConfigKey("feature.flag");
        dto.setConfigValue("true");
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("confirm system config change");
        dto.setIdempotencyKey("system-config-create-1234");
        return dto;
    }

    private static RoleMenuAssignDTO roleMenuAssignDto(Boolean dryRun) {
        RoleMenuAssignDTO dto = new RoleMenuAssignDTO();
        dto.setMenuIds(List.of(10L, 11L));
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("confirm role menu grant");
        dto.setIdempotencyKey("role-menu-grant-1234");
        return dto;
    }

    private static SysMenu menu(Long id) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        return menu;
    }
}
