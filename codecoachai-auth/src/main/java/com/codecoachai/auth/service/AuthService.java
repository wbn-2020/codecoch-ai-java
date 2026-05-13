package com.codecoachai.auth.service;

import com.codecoachai.auth.domain.dto.LoginDTO;
import com.codecoachai.auth.domain.dto.RegisterDTO;
import com.codecoachai.auth.domain.vo.CurrentUserVO;
import com.codecoachai.auth.domain.vo.LoginVO;
import com.codecoachai.auth.domain.vo.RegisterVO;

public interface AuthService {

    RegisterVO register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    void logout();

    CurrentUserVO currentUser();

    LoginVO refreshToken();
}
