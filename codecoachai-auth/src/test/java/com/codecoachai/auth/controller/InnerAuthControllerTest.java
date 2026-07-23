package com.codecoachai.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.exception.NotLoginException;
import com.codecoachai.auth.domain.vo.InnerTokenInfoVO;
import com.codecoachai.auth.service.AuthService;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.web.handler.GlobalExceptionHandler;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(OutputCaptureExtension.class)
class InnerAuthControllerTest {

    private static final String TOKEN = "revoked-secret-token-7f4a";

    private AuthService authService;
    private InnerAuthController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        controller = new InnerAuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void successReturnsGatewayTokenFieldsInHttp200ResultEnvelope() throws Exception {
        InnerTokenInfoVO tokenInfo = new InnerTokenInfoVO();
        tokenInfo.setUserId(42L);
        tokenInfo.setUsername("gateway-user");
        tokenInfo.setNickname("Gateway User");
        tokenInfo.setRoles(List.of("USER", "ADMIN"));
        tokenInfo.setPermissions(List.of("resume:read", "interview:write"));
        when(authService.tokenInfo()).thenReturn(tokenInfo);

        mockMvc.perform(get("/inner/auth/token-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SUCCESS.getMessage()))
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.username").value("gateway-user"))
                .andExpect(jsonPath("$.data.nickname").value("Gateway User"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.roles[1]").value("ADMIN"))
                .andExpect(jsonPath("$.data.permissions[0]").value("resume:read"))
                .andExpect(jsonPath("$.data.permissions[1]").value("interview:write"));
    }

    @ParameterizedTest(name = "{0} token uses the token-invalid envelope")
    @MethodSource("invalidTokenFailures")
    void invalidTokenNeverBecomesServerErrorOrLeaksToken(
            String state, RuntimeException failure, CapturedOutput output) throws Exception {
        when(authService.tokenInfo()).thenThrow(failure);

        MvcResult result = mockMvc.perform(get("/inner/auth/token-info")
                                .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_INVALID.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.TOKEN_INVALID.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(TOKEN);
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void unrelatedBusinessExceptionPropagatesUnchanged() {
        BusinessException failure = new BusinessException(ErrorCode.FORBIDDEN);
        when(authService.tokenInfo()).thenThrow(failure);

        assertThatThrownBy(controller::tokenInfo).isSameAs(failure);
    }

    @Test
    void businessExceptionWithNullCodePropagatesUnchanged() {
        BusinessException failure = new BusinessException((Integer) null, "missing error code");
        when(authService.tokenInfo()).thenThrow(failure);

        assertThatThrownBy(controller::tokenInfo).isSameAs(failure);
    }

    @Test
    void runtimeExceptionPropagatesUnchanged() {
        IllegalStateException failure = new IllegalStateException("auth store unavailable");
        when(authService.tokenInfo()).thenThrow(failure);

        assertThatThrownBy(controller::tokenInfo).isSameAs(failure);
    }

    private static Stream<Arguments> invalidTokenFailures() {
        return Stream.of(
                Arguments.of("revoked", new BusinessException(ErrorCode.TOKEN_INVALID)),
                Arguments.of("unknown", new BusinessException(ErrorCode.TOKEN_INVALID)),
                Arguments.of("unauthorized", new BusinessException(ErrorCode.UNAUTHORIZED)),
                Arguments.of("expired", NotLoginException.newInstance(
                        "login", NotLoginException.TOKEN_TIMEOUT, "expired token " + TOKEN, TOKEN)),
                Arguments.of("malformed", NotLoginException.newInstance(
                        "login", NotLoginException.NO_PREFIX, "malformed token " + TOKEN, TOKEN)));
    }
}
