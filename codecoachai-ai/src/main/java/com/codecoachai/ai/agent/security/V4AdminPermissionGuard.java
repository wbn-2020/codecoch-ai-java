package com.codecoachai.ai.agent.security;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.util.SecurityAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class V4AdminPermissionGuard {

    private final JdbcTemplate jdbcTemplate;

    public void require(String permissionCode) {
        if (!has(permissionCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public boolean has(String permissionCode) {
        SecurityAssert.requireAdmin();
        if (!StringUtils.hasText(permissionCode)) {
            return false;
        }
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
                JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.deleted = 0
                JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 1
                WHERE ur.deleted = 0
                  AND ur.user_id = ?
                  AND m.permission_code = ?
                """, Integer.class, userId, permissionCode);
        return count != null && count > 0;
    }
}
