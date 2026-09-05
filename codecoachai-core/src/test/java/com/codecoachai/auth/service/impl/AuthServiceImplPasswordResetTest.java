package com.codecoachai.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.auth.domain.dto.ForgotPasswordDTO;
import com.codecoachai.auth.domain.dto.InnerResetPasswordDTO;
import com.codecoachai.auth.domain.dto.ResetPasswordDTO;
import com.codecoachai.auth.domain.vo.ForgotPasswordVO;
import com.codecoachai.auth.domain.vo.InnerUserAuthVO;
import com.codecoachai.auth.domain.vo.ResetPasswordVO;
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
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import com.codecoachai.common.redis.util.RedisCacheHelper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplPasswordResetTest {

    private static final String TOKEN = "valid-reset-token";
    private static final String NEW_PASSWORD = "NewPassword123";
    private static final String GENERIC_MESSAGE =
            "密码重置请求已受理。如果账号存在，请按通知渠道中的指引完成重置。";

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

    private ExecutorService executorService;

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void successfulResetUpdatesPasswordRevokesSessionsThenAtomicallyConsumesToken() {
        stubResetLockExecution();
        stubSuccessfulReset();

        ResetPasswordVO result = authService.resetPassword(resetPasswordDto());

        assertThat(result.getMessage()).isEqualTo("Password has been reset.");
        ArgumentCaptor<InnerResetPasswordDTO> dtoCaptor = ArgumentCaptor.forClass(InnerResetPasswordDTO.class);
        InOrder order = inOrder(userFeignClient, authSessionRevocationService, passwordResetTokenStore);
        order.verify(userFeignClient).resetPassword(eq(42L), dtoCaptor.capture());
        order.verify(authSessionRevocationService).revokeAll(42L);
        order.verify(passwordResetTokenStore).consume(TOKEN);
        assertThat(dtoCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        verify(passwordResetSecurityLogRecorder).recordCompleted(42L);
    }

    @Test
    void passwordUpdateFailureLeavesTokenAvailableForRetry() {
        stubResetLockExecution();
        when(passwordResetTokenStore.findUserId(TOKEN)).thenReturn("42");
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("encoded-password");
        when(userFeignClient.resetPassword(eq(42L), any(InnerResetPasswordDTO.class)))
                .thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR));

        assertThatThrownBy(() -> authService.resetPassword(resetPasswordDto()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());

        verify(authSessionRevocationService, never()).revokeAll(anyLong());
        verify(passwordResetTokenStore, never()).consume(TOKEN);
    }

    @Test
    void sessionRevocationFailureLeavesTokenAvailableForRetry() {
        stubResetLockExecution();
        when(passwordResetTokenStore.findUserId(TOKEN)).thenReturn("42");
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("encoded-password");
        when(userFeignClient.resetPassword(eq(42L), any(InnerResetPasswordDTO.class)))
                .thenReturn(Result.success());
        doThrow(new IllegalStateException("session store unavailable"))
                .when(authSessionRevocationService).revokeAll(42L);

        assertThatThrownBy(() -> authService.resetPassword(resetPasswordDto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("session store unavailable");

        verify(passwordResetTokenStore, never()).consume(TOKEN);
    }

    @Test
    void concurrentRequestsAllowOnlyOnePasswordUpdate() throws Exception {
        when(passwordResetTokenStore.lockKey(TOKEN)).thenReturn("auth:password-reset-lock:fingerprint");
        AtomicBoolean lockHeld = new AtomicBoolean();
        CountDownLatch updateEntered = new CountDownLatch(1);
        CountDownLatch releaseUpdate = new CountDownLatch(1);
        doAnswer(invocation -> {
            Supplier<?> task = invocation.getArgument(3);
            Supplier<?> onLockFailure = invocation.getArgument(4);
            if (!lockHeld.compareAndSet(false, true)) {
                return onLockFailure.get();
            }
            try {
                return task.get();
            } finally {
                lockHeld.set(false);
            }
        }).when(distributedLockHelper)
                .tryLockAndCall(anyString(), anyLong(), anyLong(), any(), any());
        when(passwordResetTokenStore.findUserId(TOKEN)).thenReturn("42");
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("encoded-password");
        when(userFeignClient.resetPassword(eq(42L), any(InnerResetPasswordDTO.class)))
                .thenAnswer(invocation -> {
                    updateEntered.countDown();
                    assertTrue(releaseUpdate.await(5, TimeUnit.SECONDS));
                    return Result.<Void>success();
                });
        when(passwordResetTokenStore.consume(TOKEN)).thenReturn("42");

        executorService = Executors.newFixedThreadPool(2);
        Future<ResetPasswordVO> first = executorService.submit(() -> authService.resetPassword(resetPasswordDto()));
        assertTrue(updateEntered.await(5, TimeUnit.SECONDS));
        Future<BusinessException> second = executorService.submit(() -> {
            try {
                authService.resetPassword(resetPasswordDto());
                return null;
            } catch (BusinessException ex) {
                return ex;
            }
        });

        BusinessException rejected = second.get(5, TimeUnit.SECONDS);
        releaseUpdate.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).getMessage()).isEqualTo("Password has been reset.");
        assertThat(rejected).isNotNull();
        assertThat(rejected.getCode()).isEqualTo(ErrorCode.TOKEN_INVALID.getCode());
        verify(userFeignClient, times(1)).resetPassword(eq(42L), any(InnerResetPasswordDTO.class));
        verify(passwordResetTokenStore, times(1)).consume(TOKEN);
    }

    @Test
    void unknownAccountReturnsSameGenericAcceptanceResponse() {
        ForgotPasswordDTO dto = forgotPasswordDto();
        when(userFeignClient.getByEmail("user@example.com"))
                .thenReturn(Result.fail(ErrorCode.USER_NOT_FOUND));

        ForgotPasswordVO result = authService.forgotPassword(dto);

        assertGenericAcceptance(result);
        verify(passwordResetTokenStore, never()).issue(anyString(), anyLong(), any());
        verify(passwordResetDeliveryService, never()).sendResetToken(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void deliveryFailureReturnsGenericResponseAndRemovesUndeliveredToken() {
        ForgotPasswordDTO dto = forgotPasswordDto();
        when(userFeignClient.getByEmail("user@example.com")).thenReturn(Result.success(enabledUser()));
        doThrow(new IllegalStateException("mail unavailable"))
                .when(passwordResetDeliveryService)
                .sendResetToken(eq(42L), eq("user@example.com"), anyString(), eq(900L));

        ForgotPasswordVO result = authService.forgotPassword(dto);

        assertGenericAcceptance(result);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetTokenStore).issue(tokenCaptor.capture(), eq(42L), eq(java.time.Duration.ofMinutes(15)));
        verify(passwordResetTokenStore).delete(tokenCaptor.getValue());
    }

    @Test
    void accountLookupFailureReturnsGenericResponseWithoutIssuingToken() {
        ForgotPasswordDTO dto = forgotPasswordDto();
        when(userFeignClient.getByEmail("user@example.com"))
                .thenThrow(new IllegalStateException("user service unavailable"));

        ForgotPasswordVO result = authService.forgotPassword(dto);

        assertGenericAcceptance(result);
        verify(passwordResetTokenStore, never()).issue(anyString(), anyLong(), any());
    }

    private void stubSuccessfulReset() {
        when(passwordResetTokenStore.findUserId(TOKEN)).thenReturn("42");
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("encoded-password");
        when(userFeignClient.resetPassword(eq(42L), any(InnerResetPasswordDTO.class)))
                .thenReturn(Result.success());
        when(passwordResetTokenStore.consume(TOKEN)).thenReturn("42");
    }

    private void stubResetLockExecution() {
        when(passwordResetTokenStore.lockKey(TOKEN)).thenReturn("auth:password-reset-lock:fingerprint");
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockHelper)
                .tryLockAndCall(anyString(), anyLong(), anyLong(), any(), any());
    }

    private ResetPasswordDTO resetPasswordDto() {
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setToken(TOKEN);
        dto.setNewPassword(NEW_PASSWORD);
        dto.setConfirmPassword(NEW_PASSWORD);
        return dto;
    }

    private ForgotPasswordDTO forgotPasswordDto() {
        ForgotPasswordDTO dto = new ForgotPasswordDTO();
        dto.setEmail(" User@Example.com ");
        return dto;
    }

    private InnerUserAuthVO enabledUser() {
        InnerUserAuthVO user = new InnerUserAuthVO();
        user.setId(42L);
        user.setStatus(SecurityConstants.USER_STATUS_ENABLED);
        return user;
    }

    private void assertGenericAcceptance(ForgotPasswordVO result) {
        assertThat(result.getMessage()).isEqualTo(GENERIC_MESSAGE);
        assertThat(result.getExpiresInSeconds()).isEqualTo(900L);
    }
}
