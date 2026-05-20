package com.codecoachai.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.codecoachai.auth.domain.dto.ForgotPasswordDTO;
import com.codecoachai.auth.domain.dto.InnerCreateUserDTO;
import com.codecoachai.auth.domain.dto.InnerResetPasswordDTO;
import com.codecoachai.auth.domain.dto.LoginDTO;
import com.codecoachai.auth.domain.dto.RegisterDTO;
import com.codecoachai.auth.domain.dto.ResetPasswordDTO;
import com.codecoachai.auth.domain.vo.CurrentUserVO;
import com.codecoachai.auth.domain.vo.ForgotPasswordVO;
import com.codecoachai.auth.domain.vo.InnerCreateUserVO;
import com.codecoachai.auth.domain.vo.InnerTokenInfoVO;
import com.codecoachai.auth.domain.vo.InnerUserAuthVO;
import com.codecoachai.auth.domain.vo.InnerUserBasicVO;
import com.codecoachai.auth.domain.vo.LoginVO;
import com.codecoachai.auth.domain.vo.RegisterVO;
import com.codecoachai.auth.domain.vo.ResetPasswordVO;
import com.codecoachai.auth.feign.UserFeignClient;
import com.codecoachai.auth.log.LoginLogRecorder;
import com.codecoachai.auth.log.PasswordResetSecurityLogRecorder;
import com.codecoachai.auth.service.AuthService;
import com.codecoachai.auth.service.PasswordResetDeliveryService;
import com.codecoachai.common.core.constant.SecurityConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.redis.util.RedisCacheHelper;
import com.codecoachai.common.security.context.LoginUserContext;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long RESET_TOKEN_TTL_SECONDS = 15 * 60L;
    private static final long RESET_REQUEST_LIMIT_TTL_SECONDS = 60L;
    private static final String RESET_TOKEN_KEY_PREFIX = "auth:password-reset:";
    private static final String RESET_REQUEST_LIMIT_KEY_PREFIX = "auth:password-reset-limit:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserFeignClient userFeignClient;
    private final PasswordEncoder passwordEncoder;
    private final RedisCacheHelper redisCacheHelper;
    private final LoginLogRecorder loginLogRecorder;
    private final PasswordResetDeliveryService passwordResetDeliveryService;
    private final PasswordResetSecurityLogRecorder passwordResetSecurityLogRecorder;

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
        InnerUserAuthVO user;
        try {
            user = FeignResultUtils.unwrap(userFeignClient.getByUsername(dto.getUsername()));
        } catch (BusinessException ex) {
            loginLogRecorder.recordFailed(dto.getUsername(), "PASSWORD", ex.getMessage());
            throw ex;
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            loginLogRecorder.recordFailed(dto.getUsername(), "PASSWORD", "密码错误");
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        if (!SecurityConstants.USER_STATUS_ENABLED.equals(user.getStatus())) {
            loginLogRecorder.recordFailed(dto.getUsername(), "PASSWORD", "账号已禁用");
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        StpUtil.login(user.getId());
        List<String> roles = user.getRoles() == null ? List.of() : user.getRoles();
        StpUtil.getSession().set("username", user.getUsername());
        StpUtil.getSession().set("nickname", StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        StpUtil.getSession().set("roles", roles);
        String token = StpUtil.getTokenValue();

        loginLogRecorder.recordSuccess(user.getId(), user.getUsername(), "PASSWORD");

        CurrentUserVO currentUser = toCurrentUser(user.getId(), user.getUsername(), user.getNickname(),
                user.getAvatarUrl(), user.getEmail(), roles);
        return buildLoginVO(token, currentUser, roles);
    }

    @Override
    public ForgotPasswordVO forgotPassword(ForgotPasswordDTO dto) {
        String email = dto.getEmail() == null ? "" : dto.getEmail().trim().toLowerCase();
        String limitKey = resetRequestLimitKey(email);
        if (StringUtils.hasText(redisCacheHelper.get(limitKey))) {
            passwordResetSecurityLogRecorder.recordRejected("RATE_LIMIT");
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Password reset requests are too frequent");
        }
        redisCacheHelper.set(limitKey, "1", Duration.ofSeconds(RESET_REQUEST_LIMIT_TTL_SECONDS));

        ForgotPasswordVO vo = new ForgotPasswordVO();
        vo.setMessage("Password reset request accepted. If the account exists, follow the instructions sent through the configured notification channel.");
        vo.setExpiresInSeconds(RESET_TOKEN_TTL_SECONDS);
        try {
            InnerUserAuthVO user = FeignResultUtils.unwrap(userFeignClient.getByEmail(email));
            if (!SecurityConstants.USER_STATUS_ENABLED.equals(user.getStatus())) {
                passwordResetSecurityLogRecorder.recordRequested(email, "ACCOUNT_DISABLED");
                log.info("Password reset request ignored for disabled account email={}", maskEmail(email));
                return vo;
            }
            String token = newResetToken();
            redisCacheHelper.set(resetTokenKey(token), String.valueOf(user.getId()), Duration.ofSeconds(RESET_TOKEN_TTL_SECONDS));
            passwordResetDeliveryService.sendResetToken(user.getId(), email, token, RESET_TOKEN_TTL_SECONDS);
            passwordResetSecurityLogRecorder.recordRequested(email, "TOKEN_ISSUED");
            log.info("Password reset request accepted userId={} email={} ttlSeconds={}", user.getId(), maskEmail(email), RESET_TOKEN_TTL_SECONDS);
        } catch (BusinessException ex) {
            if (ex.getCode() == null || ex.getCode() != ErrorCode.USER_NOT_FOUND.getCode()) {
                passwordResetSecurityLogRecorder.recordRequested(email, "LOOKUP_FAILED");
                throw ex;
            }
            passwordResetSecurityLogRecorder.recordRequested(email, "ACCOUNT_NOT_FOUND");
            log.info("Password reset request accepted for non-existing email={}", maskEmail(email));
        }
        return vo;
    }

    @Override
    public ResetPasswordVO resetPassword(ResetPasswordDTO dto) {
        if (StringUtils.hasText(dto.getConfirmPassword()) && !dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }
        if (!StringUtils.hasText(dto.getToken())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        String tokenKey = resetTokenKey(dto.getToken());
        String userId = redisCacheHelper.get(tokenKey);
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Long resetUserId;
        try {
            resetUserId = Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            redisCacheHelper.delete(tokenKey);
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        InnerResetPasswordDTO innerDto = new InnerResetPasswordDTO();
        innerDto.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        FeignResultUtils.unwrap(userFeignClient.resetPassword(resetUserId, innerDto));
        redisCacheHelper.delete(tokenKey);
        passwordResetSecurityLogRecorder.recordCompleted(resetUserId);

        ResetPasswordVO vo = new ResetPasswordVO();
        vo.setMessage("Password has been reset.");
        return vo;
    }

    @Override
    public void logout() {
        if (StpUtil.isLogin()) {
            Long userId = StpUtil.getLoginIdAsLong();
            String username = (String) StpUtil.getSession().get("username");
            StpUtil.logout();
            loginLogRecorder.recordLogout(userId, username);
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
        if (StpUtil.isLogin()) {
            Long userId = Long.valueOf(StpUtil.getLoginIdAsString());
            InnerUserBasicVO user = FeignResultUtils.unwrap(userFeignClient.getInnerUser(userId));
            if (!SecurityConstants.USER_STATUS_ENABLED.equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.USER_DISABLED);
            }
            List<String> roles = user.getRoles() == null ? List.of() : user.getRoles();
            StpUtil.getSession().set("username", user.getUsername());
            StpUtil.getSession().set("nickname", StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
            StpUtil.getSession().set("roles", roles);
            CurrentUserVO currentUser = toCurrentUser(user.getId(), user.getUsername(), user.getNickname(),
                    user.getAvatarUrl(), user.getEmail(), roles);
            return buildLoginVO(StpUtil.getTokenValue(), currentUser, roles);
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
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

    private LoginVO buildLoginVO(String token, CurrentUserVO currentUser, List<String> roles) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setTokenName("Authorization");
        vo.setExpireTime(LocalDateTime.now().plusDays(1).format(DATE_TIME_FORMATTER));
        vo.setUserInfo(currentUser);
        vo.setRoles(roles == null ? List.of() : roles);
        return vo;
    }

    private String newResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String resetTokenKey(String token) {
        return RESET_TOKEN_KEY_PREFIX + token;
    }

    private String resetRequestLimitKey(String email) {
        return RESET_REQUEST_LIMIT_KEY_PREFIX + email;
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

}
