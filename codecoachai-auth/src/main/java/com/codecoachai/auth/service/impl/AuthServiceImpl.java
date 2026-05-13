package com.codecoachai.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.codecoachai.auth.domain.dto.InnerCreateUserDTO;
import com.codecoachai.auth.domain.dto.LoginDTO;
import com.codecoachai.auth.domain.dto.RegisterDTO;
import com.codecoachai.auth.domain.vo.CurrentUserVO;
import com.codecoachai.auth.domain.vo.InnerCreateUserVO;
import com.codecoachai.auth.domain.vo.InnerTokenInfoVO;
import com.codecoachai.auth.domain.vo.InnerUserAuthVO;
import com.codecoachai.auth.domain.vo.InnerUserBasicVO;
import com.codecoachai.auth.domain.vo.LoginVO;
import com.codecoachai.auth.domain.vo.RegisterVO;
import com.codecoachai.auth.feign.UserFeignClient;
import com.codecoachai.auth.service.AuthService;
import com.codecoachai.common.core.constant.SecurityConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.context.LoginUserContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserFeignClient userFeignClient;
    private final PasswordEncoder passwordEncoder;

    @Override
    public RegisterVO register(RegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }
        InnerCreateUserDTO innerDto = new InnerCreateUserDTO();
        innerDto.setUsername(dto.getUsername());
        innerDto.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        innerDto.setNickname(dto.getNickname());
        innerDto.setEmail(dto.getEmail());
        InnerCreateUserVO innerUser = FeignResultUtils.unwrap(userFeignClient.createUser(innerDto));

        RegisterVO vo = new RegisterVO();
        vo.setUserId(innerUser.getUserId());
        vo.setUsername(innerUser.getUsername());
        vo.setNickname(innerUser.getNickname());
        return vo;
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        InnerUserAuthVO user = FeignResultUtils.unwrap(userFeignClient.getByUsername(dto.getUsername()));
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        if (!SecurityConstants.USER_STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        StpUtil.login(user.getId());
        List<String> roles = user.getRoles() == null ? List.of() : user.getRoles();
        StpUtil.getSession().set("username", user.getUsername());
        StpUtil.getSession().set("nickname", StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        StpUtil.getSession().set("roles", roles);
        String token = StpUtil.getTokenValue();

        CurrentUserVO currentUser = toCurrentUser(user.getId(), user.getUsername(), user.getNickname(),
                user.getAvatarUrl(), user.getEmail(), roles);
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setTokenName("Authorization");
        vo.setExpireTime(LocalDateTime.now().plusDays(1).format(DATE_TIME_FORMATTER));
        vo.setUserInfo(currentUser);
        vo.setRoles(roles);
        return vo;
    }

    @Override
    public void logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
    }

    @Override
    public CurrentUserVO currentUser() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null && StpUtil.isLogin()) {
            userId = Long.valueOf(StpUtil.getLoginIdAsString());
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        InnerUserBasicVO user = FeignResultUtils.unwrap(userFeignClient.getInnerUser(userId));
        return toCurrentUser(user.getId(), user.getUsername(), user.getNickname(),
                user.getAvatarUrl(), user.getEmail(), user.getRoles());
    }

    @Override
    public LoginVO refreshToken() {
        throw new BusinessException(ErrorCode.PARAM_ERROR, "V1 暂未实现刷新 Token");
    }

    @Override
    public InnerTokenInfoVO tokenInfo() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = Long.valueOf(StpUtil.getLoginIdAsString());
        InnerUserBasicVO user = FeignResultUtils.unwrap(userFeignClient.getInnerUser(userId));
        if (!SecurityConstants.USER_STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        InnerTokenInfoVO vo = new InnerTokenInfoVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        vo.setRoles(user.getRoles() == null ? List.of() : user.getRoles());
        return vo;
    }

    private CurrentUserVO toCurrentUser(Long id, String username, String nickname, String avatarUrl,
                                        String email, List<String> roles) {
        CurrentUserVO vo = new CurrentUserVO();
        vo.setId(id);
        vo.setUsername(username);
        vo.setNickname(StringUtils.hasText(nickname) ? nickname : username);
        vo.setAvatarUrl(avatarUrl);
        vo.setEmail(email);
        vo.setRoles(roles == null ? List.of() : roles);
        return vo;
    }
}
