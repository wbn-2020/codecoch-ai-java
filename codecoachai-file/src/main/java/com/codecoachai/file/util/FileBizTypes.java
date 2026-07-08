package com.codecoachai.file.util;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class FileBizTypes {

    private static final Set<String> ALLOWED = Set.of(
            "RESUME",
            "AVATAR",
            "ATTACHMENT",
            "INTERVIEW_VOICE"
    );

    private FileBizTypes() {
    }

    public static String requireAllowed(String bizType) {
        String normalized = normalizeOrNull(bizType);
        if (normalized == null || !ALLOWED.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file bizType is not supported");
        }
        return normalized;
    }

    public static String normalizeOrNull(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return null;
        }
        return bizType.trim().toUpperCase(Locale.ROOT);
    }

    public static String directoryName(String bizType) {
        return requireAllowed(bizType).toLowerCase(Locale.ROOT);
    }
}
