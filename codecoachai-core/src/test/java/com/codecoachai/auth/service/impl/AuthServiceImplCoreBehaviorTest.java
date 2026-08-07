package com.codecoachai.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.codecoachai.auth.domain.dto.InnerCreateUserDTO;
import com.codecoachai.auth.domain.dto.LoginDTO;
import com.codecoachai.auth.domain.dto.RegisterDTO;
import com.codecoachai.auth.domain.vo.CurrentUserVO;
import com.codecoachai.auth.domain.vo.InnerCreateUserVO;
import com.codecoachai.auth.domain.vo.InnerUserAuthVO;
import com.codecoachai.auth.domain.vo.InnerUserBasicVO;
import com.codecoachai.auth.domain.vo.LoginVO;
import com.codecoachai.auth.domain.vo.RegisterVO;
import com.codecoachai.auth.feign.UserFeignClient;
import com.codecoachai.auth.log.LoginLogRecorder;
import com.codecoachai.auth.log.PasswordResetSecurityLogRecorder;
import com.codecoachai.auth.service.AuthPermissionResolver;
import com.codecoachai.auth.service.AuthSessionRevocationService;
import com.codecoachai.auth.service.PasswordResetDeliveryService;
import com.codecoachai.auth.service.PasswordResetTokenStore;
import com.codecoachai.common.core.constant.SecurityConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.redis.constant.RedisKeyConstants;
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import com.codecoachai.common.redis.util.RedisCacheHelper;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplCoreBehaviorTest {

    private static final Long USER_ID = 42L;
    private static final String USERNAME = "alice";
    private static final String RAW_PASSWORD = "Password123";
    private static final String TOKEN = "auth-token";
    private static final List<String> ROLES = List.of("USER", "COACH");
    private static final List<String> PERMISSIONS = List.of("profile:read", "interview:write");

    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RedisCacheHelper redisCacheHelper;
    @Mock
    private LoginLogRecorder loginLogRecorder;
    @Mock
    private PasswordResetDeliveryService passwordResetDeliveryService;
    @Mock
    private PasswordResetSecurityLogRecorder passwordResetSecurityLogRecorder;
    @Mock
    private AuthPermissionResolver authPermissionResolver;
    @Mock
    private PasswordResetTokenStore passwordResetTokenStore;
    @Mock
    private DistributedLockHelper distributedLockHelper;
    @Mock
    private AuthSessionRevocationService authSessionRevocationService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        LoginUserContext.clear();
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void registerEncodesPasswordDelegatesUserCreationAndMapsResponse() {
        RegisterDTO dto = registerDto();
        InnerCreateUserVO createdUser = new InnerCreateUserVO();
        createdUser.setUserId(USER_ID);
        createdUser.setUsername(USERNAME);
        createdUser.setNickname("Alice");
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("encoded-password");
        when(userFeignClient.createUser(any(InnerCreateUserDTO.class))).thenReturn(Result.success(createdUser));

        RegisterVO result = authService.register(dto);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getUsername()).isEqualTo(USERNAME);
        assertThat(result.getNickname()).isEqualTo("Alice");
        ArgumentCaptor<InnerCreateUserDTO> captor = ArgumentCaptor.forClass(InnerCreateUserDTO.class);
        verify(userFeignClient).createUser(captor.capture());
        assertThat(captor.getValue())
                .extracting(
                        InnerCreateUserDTO::getUsername,
                        InnerCreateUserDTO::getPasswordHash,
                        InnerCreateUserDTO::getNickname,
                        InnerCreateUserDTO::getEmail)
                .containsExactly(USERNAME, "encoded-password", "Alice", "alice@example.com");
        verify(passwordEncoder).encode(RAW_PASSWORD);
    }

    @Test
    void registerRejectsMismatchedConfirmationBeforeEncodingOrRemoteCall() {
        RegisterDTO dto = registerDto();
        dto.setConfirmPassword("Different123");

        assertThatThrownBy(() -> authService.register(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH.getCode());

        verifyNoInteractions(passwordEncoder, userFeignClient);
    }

    @Test
    void loginSuccessClearsFailureStateCreatesSessionAndBuildsFreshPrincipalSnapshot() {
        LoginDTO dto = loginDto();
        InnerUserAuthVO user = enabledAuthUser();
        user.setNickname(" ");
        when(redisCacheHelper.get(RedisKeyConstants.loginLockKey(USERNAME))).thenReturn(null);
        when(userFeignClient.getByUsername(USERNAME)).thenReturn(Result.success(user));
        when(passwordEncoder.matches(RAW_PASSWORD, "stored-hash")).thenReturn(true);
        when(authPermissionResolver.resolvePermissions(ROLES)).thenReturn(PERMISSIONS);
        SaSession session = mock(SaSession.class);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getSession).thenReturn(session);
            stpUtil.when(StpUtil::getTokenValue).thenReturn(TOKEN);

            LoginVO result = authService.login(dto);

            assertThat(result.getToken()).isEqualTo(TOKEN);
            assertThat(result.getTokenName()).isEqualTo("Authorization");
            assertThat(result.getExpireTime()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
            assertThat(result.getRoles()).containsExactlyElementsOf(ROLES);
            assertThat(result.getPermissions()).containsExactlyElementsOf(PERMISSIONS);
            assertCurrentUser(result.getUserInfo(), USERNAME);
            stpUtil.verify(() -> StpUtil.login(USER_ID));
        }

        verify(redisCacheHelper).delete(RedisKeyConstants.loginFailCountKey(USERNAME));
        verify(redisCacheHelper).delete(RedisKeyConstants.loginLockKey(USERNAME));
        verify(session).set("username", USERNAME);
        verify(session).set("nickname", USERNAME);
        verify(session).set("roles", ROLES);
        verify(session).set("permissions", PERMISSIONS);
        verify(loginLogRecorder).recordSuccess(USER_ID, USERNAME, "PASSWORD");
    }

    @Test
    void loginRejectsAlreadyLockedAccountBeforeUserLookup() {
        when(redisCacheHelper.get(RedisKeyConstants.loginLockKey(USERNAME))).thenReturn("1");

        assertThatThrownBy(() -> authService.login(loginDto()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode());

        verify(loginLogRecorder).recordFailed(eq(USERNAME), eq("PASSWORD"), anyString());
        verifyNoInteractions(userFeignClient, passwordEncoder, authPermissionResolver);
    }

    @Test
    void loginUserLookupFailureIsCountedLoggedAndPropagated() {
        String failKey = RedisKeyConstants.loginFailCountKey(USERNAME);
        when(redisCacheHelper.get(RedisKeyConstants.loginLockKey(USERNAME))).thenReturn(null);
        when(userFeignClient.getByUsername(USERNAME)).thenReturn(Result.fail(ErrorCode.USER_NOT_FOUND));
        when(redisCacheHelper.incrementAndExpire(failKey, Duration.ofMinutes(5))).thenReturn(1L);

        assertThatThrownBy(() -> authService.login(loginDto()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.USER_NOT_FOUND.getCode());

        verify(redisCacheHelper).incrementAndExpire(failKey, Duration.ofMinutes(5));
        verify(loginLogRecorder).recordFailed(eq(USERNAME), eq("PASSWORD"), anyString());
        verifyNoInteractions(passwordEncoder, authPermissionResolver);
    }

    @Test
    void fifthWrongPasswordLocksAccountAndKeepsGenericPasswordError() {
        InnerUserAuthVO user = enabledAuthUser();
        String failKey = RedisKeyConstants.loginFailCountKey(USERNAME);
        String lockKey = RedisKeyConstants.loginLockKey(USERNAME);
        when(redisCacheHelper.get(lockKey)).thenReturn(null);
        when(userFeignClient.getByUsername(USERNAME)).thenReturn(Result.success(user));
        when(passwordEncoder.matches(RAW_PASSWORD, "stored-hash")).thenReturn(false);
        when(redisCacheHelper.incrementAndExpire(failKey, Duration.ofMinutes(5))).thenReturn(5L);

        assertThatThrownBy(() -> authService.login(loginDto()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PASSWORD_ERROR.getCode());

        verify(redisCacheHelper).set(lockKey, "1", Duration.ofMinutes(15));
        verify(redisCacheHelper).delete(failKey);
        verify(loginLogRecorder).recordFailed(eq(USERNAME), eq("PASSWORD"), anyString());
        verifyNoInteractions(authPermissionResolver);
    }

    @Test
    void disabledAccountIsRejectedWithoutIncreasingPasswordFailureCount() {
        InnerUserAuthVO user = enabledAuthUser();
        user.setStatus(SecurityConstants.USER_STATUS_DISABLED);
        when(redisCacheHelper.get(RedisKeyConstants.loginLockKey(USERNAME))).thenReturn(null);
        when(userFeignClient.getByUsername(USERNAME)).thenReturn(Result.success(user));
        when(passwordEncoder.matches(RAW_PASSWORD, "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginDto()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.USER_DISABLED.getCode());

        verify(redisCacheHelper, never()).incrementAndExpire(anyString(), any());
        verify(redisCacheHelper, never()).delete(anyString());
        verify(loginLogRecorder).recordFailed(eq(USERNAME), eq("PASSWORD"), anyString());
        verifyNoInteractions(authPermissionResolver);
    }

    @Test
    void logoutRevokesCurrentSessionAndRecordsIdentity() {
        SaSession session = mock(SaSession.class);
        when(session.get("username")).thenReturn(USERNAME);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(true);
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
            stpUtil.when(StpUtil::getSession).thenReturn(session);

            authService.logout();

            stpUtil.verify(StpUtil::logout);
        }
        verify(loginLogRecorder).recordLogout(USER_ID, USERNAME);
    }

    @Test
    void logoutIsNoOpWhenThereIsNoCurrentSession() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(false);

            authService.logout();

            stpUtil.verify(StpUtil::logout, never());
        }
        verifyNoInteractions(loginLogRecorder);
    }

    @Test
    void currentUserUsesGatewayContextAndReturnsFreshUserPermissions() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("gateway-user").build());
        InnerUserBasicVO user = enabledBasicUser();
        user.setNickname(null);
        when(userFeignClient.getInnerUser(USER_ID)).thenReturn(Result.success(user));
        when(authPermissionResolver.resolvePermissions(ROLES)).thenReturn(PERMISSIONS);

        CurrentUserVO result = authService.currentUser();

        assertCurrentUser(result, USERNAME);
        verify(userFeignClient).getInnerUser(USER_ID);
        verify(authPermissionResolver).resolvePermissions(ROLES);
    }

    @Test
    void currentUserFallsBackToSaTokenIdentityWhenGatewayContextIsAbsent() {
        InnerUserBasicVO user = enabledBasicUser();
        user.setRoles(null);
        when(userFeignClient.getInnerUser(USER_ID)).thenReturn(Result.success(user));
        when(authPermissionResolver.resolvePermissions(List.of())).thenReturn(List.of());

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(true);
            stpUtil.when(StpUtil::getLoginIdAsString).thenReturn(USER_ID.toString());

            CurrentUserVO result = authService.currentUser();

            assertThat(result.getId()).isEqualTo(USER_ID);
            assertThat(result.getRoles()).isEmpty();
            assertThat(result.getPermissions()).isEmpty();
        }
        verify(userFeignClient).getInnerUser(USER_ID);
        verify(authPermissionResolver).resolvePermissions(List.of());
    }

    @Test
    void currentUserRejectsRequestWithoutGatewayOrSaTokenIdentity() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(false);

            assertThatThrownBy(authService::currentUser)
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        }
        verifyNoInteractions(userFeignClient, authPermissionResolver);
    }

    @Test
    void refreshTokenReloadsEnabledUserAndReplacesSessionAuthorizationSnapshot() {
        InnerUserBasicVO user = enabledBasicUser();
        user.setNickname("");
        when(userFeignClient.getInnerUser(USER_ID)).thenReturn(Result.success(user));
        when(authPermissionResolver.resolvePermissions(ROLES)).thenReturn(PERMISSIONS);
        SaSession session = mock(SaSession.class);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(true);
            stpUtil.when(StpUtil::getLoginIdAsString).thenReturn(USER_ID.toString());
            stpUtil.when(StpUtil::getSession).thenReturn(session);
            stpUtil.when(StpUtil::getTokenValue).thenReturn(TOKEN);

            LoginVO result = authService.refreshToken();

            assertThat(result.getToken()).isEqualTo(TOKEN);
            assertThat(result.getRoles()).containsExactlyElementsOf(ROLES);
            assertThat(result.getPermissions()).containsExactlyElementsOf(PERMISSIONS);
            assertCurrentUser(result.getUserInfo(), USERNAME);
        }

        verify(session).set("username", USERNAME);
        verify(session).set("nickname", USERNAME);
        verify(session).set("roles", ROLES);
        verify(session).set("permissions", PERMISSIONS);
    }

    @Test
    void refreshTokenRejectsDisabledUserBeforeUpdatingSession() {
        InnerUserBasicVO user = enabledBasicUser();
        user.setStatus(SecurityConstants.USER_STATUS_DISABLED);
        when(userFeignClient.getInnerUser(USER_ID)).thenReturn(Result.success(user));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(true);
            stpUtil.when(StpUtil::getLoginIdAsString).thenReturn(USER_ID.toString());

            assertThatThrownBy(authService::refreshToken)
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.USER_DISABLED.getCode());

            stpUtil.verify(() -> StpUtil.getSession(), never());
        }
        verifyNoInteractions(authPermissionResolver);
    }

    @Test
    void refreshTokenRejectsMissingSessionAsInvalidToken() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(false);

            assertThatThrownBy(authService::refreshToken)
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.TOKEN_INVALID.getCode());
        }
        verifyNoInteractions(userFeignClient, authPermissionResolver);
    }

    private RegisterDTO registerDto() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(USERNAME);
        dto.setPassword(RAW_PASSWORD);
        dto.setConfirmPassword(RAW_PASSWORD);
        dto.setNickname("Alice");
        dto.setEmail("alice@example.com");
        return dto;
    }

    private LoginDTO loginDto() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(USERNAME);
        dto.setPassword(RAW_PASSWORD);
        return dto;
    }

    private InnerUserAuthVO enabledAuthUser() {
        InnerUserAuthVO user = new InnerUserAuthVO();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setPasswordHash("stored-hash");
        user.setNickname("Alice");
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setEmail("alice@example.com");
        user.setStatus(SecurityConstants.USER_STATUS_ENABLED);
        user.setRoles(ROLES);
        return user;
    }

    private InnerUserBasicVO enabledBasicUser() {
        InnerUserBasicVO user = new InnerUserBasicVO();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setNickname("Alice");
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setEmail("alice@example.com");
        user.setStatus(SecurityConstants.USER_STATUS_ENABLED);
        user.setRoles(ROLES);
        return user;
    }

    private void assertCurrentUser(CurrentUserVO user, String expectedNickname) {
        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getUsername()).isEqualTo(USERNAME);
        assertThat(user.getNickname()).isEqualTo(expectedNickname);
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getRoles()).containsExactlyElementsOf(ROLES);
        assertThat(user.getPermissions()).containsExactlyElementsOf(PERMISSIONS);
    }
}
