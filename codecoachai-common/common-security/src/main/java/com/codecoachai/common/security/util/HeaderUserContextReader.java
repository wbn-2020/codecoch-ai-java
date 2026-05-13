package com.codecoachai.common.security.util;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.security.context.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.util.StringUtils;

public final class HeaderUserContextReader {

    private HeaderUserContextReader() {
    }

    public static LoginUser read(HttpServletRequest request) {
        String userIdValue = request.getHeader(HeaderConstants.USER_ID);
        if (!StringUtils.hasText(userIdValue)) {
            return null;
        }
        Long userId = parseLong(userIdValue);
        if (userId == null) {
            return null;
        }
        return LoginUser.builder()
                .userId(userId)
                .username(request.getHeader(HeaderConstants.USERNAME))
                .roles(parseRoles(request.getHeader(HeaderConstants.ROLES)))
                .build();
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> parseRoles(String roles) {
        if (!StringUtils.hasText(roles)) {
            return Collections.emptyList();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
