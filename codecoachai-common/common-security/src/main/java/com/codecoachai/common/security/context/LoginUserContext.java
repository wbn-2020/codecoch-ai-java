package com.codecoachai.common.security.context;

import com.codecoachai.common.core.constant.SecurityConstants;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class LoginUserContext {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private LoginUserContext() {
    }

    public static void setLoginUser(LoginUser loginUser) {
        CONTEXT.set(loginUser);
    }

    public static LoginUser getLoginUser() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        return Optional.ofNullable(getLoginUser()).map(LoginUser::getUserId).orElse(null);
    }

    public static String getUsername() {
        return Optional.ofNullable(getLoginUser()).map(LoginUser::getUsername).orElse(null);
    }

    public static List<String> getRoles() {
        return Optional.ofNullable(getLoginUser())
                .map(LoginUser::getRoles)
                .orElse(Collections.emptyList());
    }

    public static boolean isAdmin() {
        return hasRole(SecurityConstants.ROLE_ADMIN);
    }

    public static boolean hasRole(String roleCode) {
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        return normalizedRoleCode != null && getRoles().stream()
                .map(LoginUserContext::normalizeRoleCode)
                .anyMatch(normalizedRoleCode::equalsIgnoreCase);
    }

    private static String normalizeRoleCode(String roleCode) {
        if (roleCode == null) {
            return null;
        }
        String normalized = roleCode.trim();
        if (normalized.regionMatches(true, 0, "ROLE_", 0, 5)) {
            normalized = normalized.substring(5);
        }
        return normalized.isBlank() ? null : normalized;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
