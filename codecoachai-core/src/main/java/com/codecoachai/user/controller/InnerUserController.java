package com.codecoachai.user.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.user.domain.dto.InnerCreateUserDTO;
import com.codecoachai.user.domain.dto.InnerResetPasswordDTO;
import com.codecoachai.user.domain.vo.InnerCreateUserVO;
import com.codecoachai.user.domain.vo.InnerUserAuthVO;
import com.codecoachai.user.domain.vo.InnerUserBasicVO;
import com.codecoachai.user.domain.vo.InnerUserRoleVO;
import com.codecoachai.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/users")
public class InnerUserController {

    private final UserService userService;

    @GetMapping("/by-username")
    public Result<InnerUserAuthVO> getByUsername(@RequestParam String username) {
        return Result.success(userService.getInnerUserByUsername(username));
    }

    @GetMapping("/by-email")
    public Result<InnerUserAuthVO> getByEmail(@RequestParam String email) {
        return Result.success(userService.getInnerUserByEmail(email));
    }

    @PostMapping
    public Result<InnerCreateUserVO> createUser(@Valid @RequestBody InnerCreateUserDTO dto) {
        return Result.success(userService.createInnerUser(dto));
    }

    @GetMapping("/{id}/roles")
    public Result<InnerUserRoleVO> getRoles(@PathVariable Long id) {
        return Result.success(userService.getInnerUserRoles(id));
    }

    @GetMapping("/{id}")
    public Result<InnerUserBasicVO> getUser(@PathVariable Long id) {
        return Result.success(userService.getInnerUser(id));
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody InnerResetPasswordDTO dto) {
        userService.resetInnerPassword(id, dto);
        return Result.success();
    }
}
