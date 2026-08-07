package com.codecoachai.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.codecoachai.common.security.admin.AdminPermissionCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthSessionRevocationService {

    private final AdminPermissionCache adminPermissionCache;

    /**
     * Sa-Token logout by login id revokes every terminal token and its session for the user.
     */
    public void revokeAll(Long userId) {
        StpUtil.logout(userId);
        adminPermissionCache.invalidateUserPermissions(userId);
    }
}
