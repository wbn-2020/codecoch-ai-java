package com.codecoachai.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.user.domain.dto.UpdateUserStatusDTO;
import com.codecoachai.user.service.RoleService;
import com.codecoachai.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private RoleService roleService;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminUserController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUserController(
                userService,
                roleService,
                permissionGuard,
                operationConfirmationGuard);
    }

    @Test
    void userStatusForwardsDryRunToConfirmationGuard() {
        UpdateUserStatusDTO dto = statusDto(0, false);

        controller.updateStatus(9L, dto);

        verify(permissionGuard).require("admin:user:write");
        verify(operationConfirmationGuard).requireConfirmed(
                "user-status:9",
                true,
                false,
                "confirm user status",
                "user-status-1234");
        verify(userService).updateUserStatus(9L, dto);
        verify(operationConfirmationGuard, never()).release(any());
    }

    @Test
    void userStatusRejectsDryRunBeforeServiceMutation() {
        UpdateUserStatusDTO dto = statusDto(0, true);
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR, "dryRun rejected"))
                .when(operationConfirmationGuard)
                .requireConfirmed(
                        eq("user-status:9"),
                        eq(true),
                        eq(true),
                        eq("confirm user status"),
                        eq("user-status-1234"));

        assertThrows(BusinessException.class, () -> controller.updateStatus(9L, dto));

        verify(userService, never()).updateUserStatus(any(), any());
    }

    @Test
    void resetPasswordReleasesIdempotencyLockWhenServiceFails() {
        AdminUserController.AdminOperationConfirmDTO dto = confirmDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                "user-reset-password:9",
                true,
                false,
                "confirm account operation",
                "account-operation-1234"))
                .thenReturn("redis-lock-key");
        when(userService.resetPassword(9L)).thenThrow(new IllegalStateException("password reset failed"));

        assertThrows(IllegalStateException.class, () -> controller.resetPassword(9L, dto));

        verify(operationConfirmationGuard).release("redis-lock-key");
    }

    @Test
    void assignRolesForwardsDryRunToConfirmationGuard() {
        AdminUserController.AssignRolesDTO dto = assignRolesDto(false);

        controller.assignRoles(9L, dto);

        verify(permissionGuard).require("admin:role:assign");
        verify(operationConfirmationGuard).requireConfirmed(
                "user-assign-roles:9",
                true,
                false,
                "confirm role assignment",
                "assign-roles-1234");
        verify(roleService).assignRolesToUser(9L, List.of(1L, 2L));
    }

    @Test
    void createRoleForwardsDryRunAndReturnsRoleId() {
        AdminUserController.RoleSaveDTO dto = roleSaveDto(false);
        when(roleService.createRole("OPS", "Operations", "ops team")).thenReturn(8L);

        Long roleId = controller.createRole(dto).getData();

        assertEquals(8L, roleId);
        verify(permissionGuard).require("admin:role:write");
        verify(operationConfirmationGuard).requireConfirmed(
                "role-create:OPS",
                true,
                false,
                "confirm role save",
                "role-save-1234");
    }

    @Test
    void deleteRoleReleasesIdempotencyLockWhenServiceRejectsDelete() {
        AdminUserController.AdminOperationConfirmDTO dto = confirmDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                "role-delete:8",
                true,
                false,
                "confirm account operation",
                "account-operation-1234"))
                .thenReturn("redis-lock-key");
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR, "role still assigned"))
                .when(roleService).deleteRole(8L);

        assertThrows(BusinessException.class, () -> controller.deleteRole(8L, dto));

        verify(operationConfirmationGuard).release("redis-lock-key");
    }

    private static UpdateUserStatusDTO statusDto(Integer status, Boolean dryRun) {
        UpdateUserStatusDTO dto = new UpdateUserStatusDTO();
        dto.setStatus(status);
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("confirm user status");
        dto.setIdempotencyKey("user-status-1234");
        return dto;
    }

    private static AdminUserController.AdminOperationConfirmDTO confirmDto(Boolean dryRun) {
        AdminUserController.AdminOperationConfirmDTO dto = new AdminUserController.AdminOperationConfirmDTO();
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("confirm account operation");
        dto.setIdempotencyKey("account-operation-1234");
        return dto;
    }

    private static AdminUserController.AssignRolesDTO assignRolesDto(Boolean dryRun) {
        AdminUserController.AssignRolesDTO dto = new AdminUserController.AssignRolesDTO();
        dto.setRoleIds(List.of(1L, 2L));
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("confirm role assignment");
        dto.setIdempotencyKey("assign-roles-1234");
        return dto;
    }

    private static AdminUserController.RoleSaveDTO roleSaveDto(Boolean dryRun) {
        AdminUserController.RoleSaveDTO dto = new AdminUserController.RoleSaveDTO();
        dto.setRoleCode("OPS");
        dto.setRoleName("Operations");
        dto.setDescription("ops team");
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("confirm role save");
        dto.setIdempotencyKey("role-save-1234");
        return dto;
    }
}
