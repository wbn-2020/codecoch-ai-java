package com.codecoachai.user.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
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

    private final UserService userService;
    private final RoleService roleService;

    // ==================== 用户管理 ====================

    @GetMapping("/admin/users")
    @OperationLog(module = "user", action = "QUERY_USER", description = "查询用户列表", logArgs = false)
    public Result<PageResult<AdminUserPageVO>> pageUsers(@Valid AdminUserQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(userService.pageAdminUsers(query));
    }

    @PutMapping("/admin/users/{id}/status")
    @OperationLog(module = "user", action = "UPDATE_USER_STATUS", description = "切换用户状态")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusDTO dto) {
        SecurityAssert.requireAdmin();
        userService.updateUserStatus(id, dto);
        return Result.success();
    }

    @PostMapping("/admin/users/{id}/reset-password")
    @OperationLog(module = "user", action = "RESET_USER_PASSWORD", description = "重置用户密码", logResponse = false)
    public Result<String> resetPassword(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        // 管理端重置密码会返回一次性新密码，前端展示后应引导管理员线下安全传递。
        String newPassword = userService.resetPassword(id);
        return Result.success(newPassword);
    }

    @PostMapping("/admin/users/{id}/assign-roles")
    @OperationLog(module = "user", action = "ASSIGN_USER_ROLE", description = "分配用户角色")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody @Valid AssignRolesDTO dto) {
        SecurityAssert.requireAdmin();
        roleService.assignRolesToUser(id, dto.getRoleIds());
        return Result.success();
    }

    @GetMapping("/admin/users/{id}/roles")
    @OperationLog(module = "user", action = "QUERY_USER_ROLE", description = "查询用户角色", logArgs = false)
    public Result<List<AdminRoleVO>> getUserRoles(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(roleService.listRolesByUserId(id));
    }

    // ==================== 角色管理 ====================

    @GetMapping("/admin/roles")
    @OperationLog(module = "user", action = "QUERY_ROLE", description = "查询角色列表", logArgs = false)
    public Result<List<AdminRoleVO>> listRoles() {
        SecurityAssert.requireAdmin();
        return Result.success(roleService.listAdminRoles());
    }

    @PostMapping("/admin/roles")
    @OperationLog(module = "user", action = "CREATE_ROLE", description = "新增角色")
    public Result<Long> createRole(@Valid @RequestBody RoleSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(roleService.createRole(dto.getRoleCode(), dto.getRoleName(), dto.getDescription()));
    }

    @PutMapping("/admin/roles/{id}")
    @OperationLog(module = "user", action = "UPDATE_ROLE", description = "编辑角色")
    public Result<Void> updateRole(@PathVariable Long id, @Valid @RequestBody RoleSaveDTO dto) {
        SecurityAssert.requireAdmin();
        roleService.updateRole(id, dto.getRoleName(), dto.getDescription());
        return Result.success();
    }

    @DeleteMapping("/admin/roles/{id}")
    @OperationLog(module = "user", action = "DELETE_ROLE", description = "删除角色")
    public Result<Void> deleteRole(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        roleService.deleteRole(id);
        return Result.success();
    }

    @PutMapping("/admin/roles/{id}/status")
    @OperationLog(module = "user", action = "UPDATE_ROLE_STATUS", description = "切换角色状态")
    public Result<Void> updateRoleStatus(@PathVariable Long id, @RequestBody UpdateUserStatusDTO dto) {
        SecurityAssert.requireAdmin();
        roleService.updateRoleStatus(id, dto.getStatus());
        return Result.success();
    }

    // ==================== DTO ====================

    @Data
    public static class AssignRolesDTO {
        @NotNull(message = "角色ID列表不能为空")
        @NotEmpty(message = "角色ID列表不能为空")
        private List<Long> roleIds;
    }

    @Data
    public static class RoleSaveDTO {
        private String roleCode;
        private String roleName;
        private String description;
    }
}
