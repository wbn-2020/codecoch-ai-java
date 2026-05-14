package com.codecoachai.common.security.util;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;

public final class SecurityAssert {

    private SecurityAssert() {
    }

    public static Long requireLoginUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static void requireAdmin() {
        requireLoginUserId();
        if (!LoginUserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
