package com.codecoachai.common.security.admin;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AdminPermissionGuard {

    private final JdbcTemplate jdbcTemplate;

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
        Long userId = LoginUserContext.getUserId();
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
                JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.deleted = 0
                JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 1
                WHERE ur.deleted = 0
                  AND ur.user_id = ?
                  AND m.permission_code IN (%s)
                """.formatted(placeholders(codes.length)), Integer.class, buildArgs(userId, codes));
        if (count == null || count <= 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private Object[] buildArgs(Long userId, String[] codes) {
        Object[] args = new Object[codes.length + 1];
        args[0] = userId;
        System.arraycopy(codes, 0, args, 1, codes.length);
        return args;
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
