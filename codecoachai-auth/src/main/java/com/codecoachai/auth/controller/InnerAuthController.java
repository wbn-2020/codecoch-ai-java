package com.codecoachai.auth.controller;

import com.codecoachai.auth.domain.vo.InnerTokenInfoVO;
import com.codecoachai.auth.service.AuthService;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/auth")
public class InnerAuthController {

    private final AuthService authService;

    @GetMapping("/token-info")
    public Result<InnerTokenInfoVO> tokenInfo() {
        return Result.success(authService.tokenInfo());
    }
}
