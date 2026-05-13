package com.codecoachai.user.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.user.domain.dto.AdminUserQueryDTO;
import com.codecoachai.user.domain.dto.UpdateUserStatusDTO;
import com.codecoachai.user.domain.vo.AdminRoleVO;
import com.codecoachai.user.domain.vo.AdminUserPageVO;
import com.codecoachai.user.service.RoleService;
import com.codecoachai.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final RoleService roleService;

    @GetMapping("/admin/users")
    public Result<PageResult<AdminUserPageVO>> pageUsers(@Valid AdminUserQueryDTO query) {
        return Result.success(userService.pageAdminUsers(query));
    }

    @PutMapping("/admin/users/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusDTO dto) {
        userService.updateUserStatus(id, dto);
        return Result.success();
    }

    @GetMapping("/admin/roles")
    public Result<List<AdminRoleVO>> listRoles() {
        return Result.success(roleService.listAdminRoles());
    }
}
