package com.codecoachai.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.codecoachai.common.core.enums.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setValidator(validator)
                .build();
    }

    @Test
    void registerRouteDelegatesValidatedBodyAndWrapsServiceResult() throws Exception {
        RegisterVO serviceResult = new RegisterVO();
        serviceResult.setUserId(42L);
        serviceResult.setUsername("new_user");
        serviceResult.setNickname("New User");
        when(authService.register(org.mockito.ArgumentMatchers.any(RegisterDTO.class))).thenReturn(serviceResult);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new_user",
                                  "password": "Password123",
                                  "confirmPassword": "Password123",
                                  "nickname": "New User",
                                  "email": "new@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SUCCESS.getMessage()))
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.username").value("new_user"))
                .andExpect(jsonPath("$.data.nickname").value("New User"));

        ArgumentCaptor<RegisterDTO> captor = ArgumentCaptor.forClass(RegisterDTO.class);
        verify(authService).register(captor.capture());
        assertThat(captor.getValue())
                .extracting(
                        RegisterDTO::getUsername,
                        RegisterDTO::getPassword,
                        RegisterDTO::getConfirmPassword,
                        RegisterDTO::getNickname,
                        RegisterDTO::getEmail)
                .containsExactly("new_user", "Password123", "Password123", "New User", "new@example.com");
    }

    @Test
    void invalidRegisterBodyIsRejectedBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bad",
                                  "password": "short",
                                  "confirmPassword": "short",
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void loginRouteDelegatesValidatedBodyAndWrapsServiceResult() throws Exception {
        LoginVO serviceResult = loginResult("login-token");
        when(authService.login(org.mockito.ArgumentMatchers.any(LoginDTO.class))).thenReturn(serviceResult);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SUCCESS.getMessage()))
                .andExpect(jsonPath("$.data.token").value("login-token"))
                .andExpect(jsonPath("$.data.userInfo.id").value(42))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.permissions[0]").value("profile:read"));

        ArgumentCaptor<LoginDTO> captor = ArgumentCaptor.forClass(LoginDTO.class);
        verify(authService).login(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getPassword()).isEqualTo("Password123");
    }

    @Test
    void forgotPasswordRouteDelegatesValidatedBodyAndWrapsServiceResult() throws Exception {
        ForgotPasswordVO serviceResult = new ForgotPasswordVO();
        serviceResult.setMessage("accepted");
        serviceResult.setExpiresInSeconds(900L);
        when(authService.forgotPassword(org.mockito.ArgumentMatchers.any(ForgotPasswordDTO.class)))
                .thenReturn(serviceResult);

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SUCCESS.getMessage()))
                .andExpect(jsonPath("$.data.message").value("accepted"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(900));

        ArgumentCaptor<ForgotPasswordDTO> captor = ArgumentCaptor.forClass(ForgotPasswordDTO.class);
        verify(authService).forgotPassword(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void resetPasswordRouteDelegatesValidatedBodyAndWrapsServiceResult() throws Exception {
        ResetPasswordVO serviceResult = new ResetPasswordVO();
        serviceResult.setMessage("reset");
        when(authService.resetPassword(org.mockito.ArgumentMatchers.any(ResetPasswordDTO.class)))
                .thenReturn(serviceResult);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token",
                                  "newPassword": "NewPassword123",
                                  "confirmPassword": "NewPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SUCCESS.getMessage()))
                .andExpect(jsonPath("$.data.message").value("reset"));

        ArgumentCaptor<ResetPasswordDTO> captor = ArgumentCaptor.forClass(ResetPasswordDTO.class);
        verify(authService).resetPassword(captor.capture());
        assertThat(captor.getValue())
                .extracting(
                        ResetPasswordDTO::getToken,
                        ResetPasswordDTO::getNewPassword,
                        ResetPasswordDTO::getConfirmPassword)
                .containsExactly("reset-token", "NewPassword123", "NewPassword123");
    }

    @Test
    void logoutRouteDelegatesAndReturnsEmptySuccessResult() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SUCCESS.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService).logout();
    }

    @Test
    void currentUserRouteDelegatesAndWrapsServiceResult() throws Exception {
        CurrentUserVO serviceResult = currentUser();
        when(authService.currentUser()).thenReturn(serviceResult);

        mockMvc.perform(get("/auth/current-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SUCCESS.getMessage()))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.permissions[0]").value("profile:read"));

        verify(authService).currentUser();
    }

    @Test
    void refreshTokenRouteDelegatesAndWrapsServiceResult() throws Exception {
        LoginVO serviceResult = loginResult("refreshed-token");
        when(authService.refreshToken()).thenReturn(serviceResult);

        mockMvc.perform(post("/auth/refresh-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SUCCESS.getMessage()))
                .andExpect(jsonPath("$.data.token").value("refreshed-token"))
                .andExpect(jsonPath("$.data.tokenName").value("Authorization"))
                .andExpect(jsonPath("$.data.userInfo.username").value("alice"));

        verify(authService).refreshToken();
    }

    private LoginVO loginResult(String token) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setTokenName("Authorization");
        vo.setExpireTime("2026-08-06 12:00:00");
        vo.setUserInfo(currentUser());
        vo.setRoles(List.of("USER"));
        vo.setPermissions(List.of("profile:read"));
        return vo;
    }

    private CurrentUserVO currentUser() {
        CurrentUserVO vo = new CurrentUserVO();
        vo.setId(42L);
        vo.setUsername("alice");
        vo.setNickname("Alice");
        vo.setAvatarUrl("https://example.com/avatar.png");
        vo.setEmail("alice@example.com");
        vo.setRoles(List.of("USER"));
        vo.setPermissions(List.of("profile:read"));
        return vo;
    }
}
