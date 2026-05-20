package com.codecoachai.auth.controller;

import com.codecoachai.auth.domain.dto.ForgotPasswordDTO;
import com.codecoachai.auth.domain.dto.LoginDTO;
import com.codecoachai.auth.domain.dto.RegisterDTO;
import com.codecoachai.auth.domain.dto.ResetPasswordDTO;
import com.codecoachai.auth.domain.vo.CurrentUserVO;
import com.codecoachai.auth.domain.vo.ForgotPasswordVO;
import com.codecoachai.auth.domain.vo.LoginVO;
import com.codecoachai.auth.domain.vo.RegisterVO;
import com.codecoachai.auth.domain.vo.ResetPasswordVO;
import com.codecoachai.auth.service.AuthService;
import com.codecoachai.common.core.domain.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<RegisterVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(authService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @PostMapping("/forgot-password")
    public Result<ForgotPasswordVO> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
        return Result.success(authService.forgotPassword(dto));
    }

    @PostMapping("/reset-password")
    public Result<ResetPasswordVO> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        return Result.success(authService.resetPassword(dto));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @GetMapping("/current-user")
    public Result<CurrentUserVO> currentUser() {
        return Result.success(authService.currentUser());
    }

    @PostMapping("/refresh-token")
    public Result<LoginVO> refreshToken() {
        return Result.success(authService.refreshToken());
    }
}
