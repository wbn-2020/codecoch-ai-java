package com.codecoachai.user.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.user.domain.dto.AdminUserQueryDTO;
import com.codecoachai.user.domain.dto.UpdateUserStatusDTO;
import com.codecoachai.user.domain.vo.AdminRoleVO;
import com.codecoachai.user.domain.vo.AdminUserPageVO;
import com.codecoachai.user.service.RoleService;
import com.codecoachai.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.function.Supplier;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class AdminUserController {

    private static final String PERM_USER_LIST = "admin:user:list";
    private static final String PERM_USER_WRITE = "admin:user:write";
    private static final String PERM_USER_PASSWORD_RESET = "admin:user:password:reset";
    private static final String PERM_ROLE_LIST = "admin:role:list";
    private static final String PERM_ROLE_WRITE = "admin:role:write";
    private static final String PERM_ROLE_ASSIGN = "admin:role:assign";

    private final UserService userService;
    private final RoleService roleService;
    private final AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @GetMapping("/admin/users")
    @OperationLog(module = "user", action = "QUERY_USER", description = "Query admin user list", logArgs = false)
    public Result<PageResult<AdminUserPageVO>> pageUsers(@Valid AdminUserQueryDTO query) {
        permissionGuard.require(PERM_USER_LIST);
        return Result.success(userService.pageAdminUsers(query));
    }

    @PutMapping("/admin/users/{id}/status")
    @OperationLog(module = "user", action = "UPDATE_USER_STATUS", description = "Update user status",
            logArgs = false, logResponse = false)
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusDTO dto) {
        permissionGuard.require(PERM_USER_WRITE);
        return runConfirmedOperation("user-status:" + id,
                dto.getConfirm(),
                dto.getDryRun(),
                dto.getReason(),
                dto.getIdempotencyKey(),
                () -> {
                    userService.updateUserStatus(id, dto);
                    return Result.success();
                });
    }

    @PostMapping("/admin/users/{id}/reset-password")
    @OperationLog(module = "user", action = "RESET_USER_PASSWORD", description = "Reset user password",
            logArgs = false, logResponse = false)
    public Result<String> resetPassword(@PathVariable Long id,
                                        @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        permissionGuard.require(PERM_USER_PASSWORD_RESET);
        return runConfirmedOperation("user-reset-password:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> Result.success(userService.resetPassword(id)));
    }

    @PostMapping("/admin/users/{id}/assign-roles")
    @OperationLog(module = "user", action = "ASSIGN_USER_ROLE", description = "Assign user roles",
            logArgs = false, logResponse = false)
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody @Valid AssignRolesDTO dto) {
        permissionGuard.require(PERM_ROLE_ASSIGN);
        return runConfirmedOperation("user-assign-roles:" + id,
                dto.getConfirm(),
                dto.getDryRun(),
                dto.getReason(),
                dto.getIdempotencyKey(),
                () -> {
                    roleService.assignRolesToUser(id, dto.getRoleIds());
                    return Result.success();
                });
    }

    @GetMapping("/admin/users/{id}/roles")
    @OperationLog(module = "user", action = "QUERY_USER_ROLE", description = "Query user roles", logArgs = false)
    public Result<List<AdminRoleVO>> getUserRoles(@PathVariable Long id) {
        permissionGuard.require(PERM_USER_LIST);
        return Result.success(roleService.listRolesByUserId(id));
    }

    @GetMapping("/admin/roles")
    @OperationLog(module = "user", action = "QUERY_ROLE", description = "Query role list", logArgs = false)
    public Result<List<AdminRoleVO>> listRoles() {
        permissionGuard.require(PERM_ROLE_LIST);
        return Result.success(roleService.listAdminRoles());
    }

    @PostMapping("/admin/roles")
    @OperationLog(module = "user", action = "CREATE_ROLE", description = "Create role",
            logArgs = false, logResponse = false)
    public Result<Long> createRole(@Valid @RequestBody RoleSaveDTO dto) {
        permissionGuard.require(PERM_ROLE_WRITE);
        return runConfirmedOperation("role-create:" + dto.getRoleCode(),
                dto.getConfirm(),
                dto.getDryRun(),
                dto.getReason(),
                dto.getIdempotencyKey(),
                () -> Result.success(roleService.createRole(dto.getRoleCode(), dto.getRoleName(), dto.getDescription())));
    }

    @PutMapping("/admin/roles/{id}")
    @OperationLog(module = "user", action = "UPDATE_ROLE", description = "Update role",
            logArgs = false, logResponse = false)
    public Result<Void> updateRole(@PathVariable Long id, @Valid @RequestBody RoleSaveDTO dto) {
        permissionGuard.require(PERM_ROLE_WRITE);
        return runConfirmedOperation("role-update:" + id,
                dto.getConfirm(),
                dto.getDryRun(),
                dto.getReason(),
                dto.getIdempotencyKey(),
                () -> {
                    roleService.updateRole(id, dto.getRoleName(), dto.getDescription());
                    return Result.success();
                });
    }

    @DeleteMapping("/admin/roles/{id}")
    @OperationLog(module = "user", action = "DELETE_ROLE", description = "Delete role",
            logArgs = false, logResponse = false)
    public Result<Void> deleteRole(@PathVariable Long id,
                                   @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        permissionGuard.require(PERM_ROLE_WRITE);
        return runConfirmedOperation("role-delete:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    roleService.deleteRole(id);
                    return Result.success();
                });
    }

    @PutMapping("/admin/roles/{id}/status")
    @OperationLog(module = "user", action = "UPDATE_ROLE_STATUS", description = "Update role status",
            logArgs = false, logResponse = false)
    public Result<Void> updateRoleStatus(@PathVariable Long id, @RequestBody UpdateUserStatusDTO dto) {
        permissionGuard.require(PERM_ROLE_WRITE);
        return runConfirmedOperation("role-status:" + id,
                dto.getConfirm(),
                dto.getDryRun(),
                dto.getReason(),
                dto.getIdempotencyKey(),
                () -> {
                    roleService.updateRoleStatus(id, dto.getStatus());
                    return Result.success();
                });
    }

    private <T> Result<T> runConfirmedOperation(String operation, Boolean confirm, Boolean dryRun,
                                                String reason, String idempotencyKey,
                                                Supplier<Result<T>> action) {
        String lockKey = operationConfirmationGuard.requireConfirmed(operation, confirm, dryRun, reason, idempotencyKey);
        try {
            return action.get();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @Data
    public static class AssignRolesDTO {
        @NotNull(message = "roleIds must not be null")
        @NotEmpty(message = "roleIds must not be empty")
        private List<Long> roleIds;
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }

    @Data
    public static class RoleSaveDTO {
        private String roleCode;
        private String roleName;
        private String description;
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }

    @Data
    public static class AdminOperationConfirmDTO {
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }
}
