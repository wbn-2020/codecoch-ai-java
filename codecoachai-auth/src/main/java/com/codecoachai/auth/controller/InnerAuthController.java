package com.codecoachai.auth.controller;

import cn.dev33.satoken.exception.NotLoginException;
import com.codecoachai.auth.domain.vo.InnerTokenInfoVO;
import com.codecoachai.auth.service.AuthService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
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
        try {
            return Result.success(authService.tokenInfo());
        } catch (NotLoginException ex) {
            return Result.fail(ErrorCode.TOKEN_INVALID);
        } catch (BusinessException ex) {
            if (ex.getCode() != null
                    && (ErrorCode.UNAUTHORIZED.getCode() == ex.getCode()
                        || ErrorCode.TOKEN_INVALID.getCode() == ex.getCode())) {
                return Result.fail(ErrorCode.TOKEN_INVALID);
            }
            throw ex;
        }
    }
}
