package com.codecoachai.file.util;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.List;
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
    private static final Set<String> INTERVIEW_VOICE_EXTENSIONS = Set.of("webm", "wav", "mp3", "m4a", "ogg");

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

    public static boolean isExtensionAllowed(String bizType, String fileExt, List<String> configuredExtensions) {
        String normalizedBizType = requireAllowed(bizType);
        String ext = StringUtils.hasText(fileExt) ? fileExt.trim().toLowerCase(Locale.ROOT) : "";
        if ("INTERVIEW_VOICE".equals(normalizedBizType)) {
            return INTERVIEW_VOICE_EXTENSIONS.contains(ext);
        }
        if (INTERVIEW_VOICE_EXTENSIONS.contains(ext)) {
            return false;
        }
        boolean configuredAllowed = configuredExtensions != null && configuredExtensions.stream()
                .filter(StringUtils::hasText)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(ext::equals);
        return configuredAllowed;
    }

    public static boolean isInterviewVoice(String bizType) {
        return "INTERVIEW_VOICE".equals(requireAllowed(bizType));
    }
}
