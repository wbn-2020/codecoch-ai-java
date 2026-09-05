package com.codecoachai.auth.service;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import cn.dev33.satoken.stp.StpUtil;
import com.codecoachai.common.security.admin.AdminPermissionCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthSessionRevocationServiceTest {

    @Mock
    private AdminPermissionCache adminPermissionCache;

    @Test
    void revokeAllLogsOutEverySaTokenSessionAndInvalidatesPermissionCache() {
        AuthSessionRevocationService service = new AuthSessionRevocationService(adminPermissionCache);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            service.revokeAll(42L);

            stpUtil.verify(() -> StpUtil.logout(42L));
        }
        verify(adminPermissionCache).invalidateUserPermissions(42L);
    }
}
