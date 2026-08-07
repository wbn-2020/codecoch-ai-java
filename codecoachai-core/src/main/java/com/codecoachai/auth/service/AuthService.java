package com.codecoachai.auth.service;

import com.codecoachai.auth.domain.dto.ForgotPasswordDTO;
import com.codecoachai.auth.domain.dto.LoginDTO;
import com.codecoachai.auth.domain.dto.RegisterDTO;
import com.codecoachai.auth.domain.dto.ResetPasswordDTO;
import com.codecoachai.auth.domain.vo.CurrentUserVO;
import com.codecoachai.auth.domain.vo.ForgotPasswordVO;
import com.codecoachai.auth.domain.vo.InnerTokenInfoVO;
import com.codecoachai.auth.domain.vo.LoginVO;
import com.codecoachai.auth.domain.vo.RegisterVO;
import com.codecoachai.auth.domain.vo.ResetPasswordVO;

public interface AuthService {

    RegisterVO register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    ForgotPasswordVO forgotPassword(ForgotPasswordDTO dto);

    ResetPasswordVO resetPassword(ResetPasswordDTO dto);

    void logout();

    CurrentUserVO currentUser();

    LoginVO refreshToken();

    InnerTokenInfoVO tokenInfo();
}
