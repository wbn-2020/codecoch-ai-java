package com.codecoachai.common.security.admin;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.Arrays;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AdminPermissionGuard {

    private static final String ADMIN_OVERVIEW_PERMISSION = "admin:system:overview";

    private final AdminPermissionCache adminPermissionCache;

    public void require(String permissionCode) {
        requireAny(permissionCode);
    }

    public void requireAny(String... permissionCodes) {
        SecurityAssert.requireAdmin();
        String[] codes = Arrays.stream(permissionCodes == null ? new String[0] : permissionCodes)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toArray(String[]::new);
        if (codes.length == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (LoginUserContext.isAdmin() && codes.length == 1 && ADMIN_OVERVIEW_PERMISSION.equals(codes[0])) {
            return;
        }
        Long userId = LoginUserContext.getUserId();
        Set<String> permissions = adminPermissionCache.getUserPermissions(userId);
        boolean matched = Arrays.stream(codes).anyMatch(permissions::contains);
        if (!matched) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
