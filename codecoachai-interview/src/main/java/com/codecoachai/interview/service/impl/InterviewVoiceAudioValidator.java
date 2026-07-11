package com.codecoachai.interview.service.impl;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.feign.vo.InnerFileInfoVO;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

final class InterviewVoiceAudioValidator {

    static final String BIZ_TYPE = "INTERVIEW_VOICE";
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final Map<String, Set<String>> MIME_TYPES_BY_EXTENSION = Map.of(
            "webm", Set.of("audio/webm", "video/webm"),
            "wav", Set.of("audio/wav", "audio/wave", "audio/x-wav"),
            "mp3", Set.of("audio/mpeg", "audio/mp3"),
            "m4a", Set.of("audio/mp4", "audio/x-m4a", "audio/m4a"),
            "ogg", Set.of("audio/ogg", "application/ogg")
    );

    private InterviewVoiceAudioValidator() {
    }

    static String validateFile(InnerFileInfoVO file, String clientMimeType, long maxAudioBytes) {
        if (file == null
                || !BIZ_TYPE.equalsIgnoreCase(file.getBizType())
                || !STATUS_AVAILABLE.equalsIgnoreCase(file.getStatus())) {
            throw invalid("Voice audio file is unavailable.");
        }
        String extension = normalizeExtension(file.getFileExt());
        if (!MIME_TYPES_BY_EXTENSION.containsKey(extension)) {
            throw invalid("Voice audio extension is not supported.");
        }
        validateFilenameExtension(file.getOriginalFilename(), extension);
        validateFilenameExtension(file.getStoredFilename(), extension);

        String serverMimeType = normalizeMimeType(file.getMimeType());
        if (!MIME_TYPES_BY_EXTENSION.get(extension).contains(serverMimeType)) {
            throw invalid("Voice audio MIME type does not match its extension.");
        }
        if (StringUtils.hasText(clientMimeType)
                && !serverMimeType.equals(normalizeMimeType(clientMimeType))) {
            throw invalid("Voice audio MIME type does not match server metadata.");
        }

        long effectiveMaxBytes = Math.max(1L, maxAudioBytes);
        if (file.getFileSize() == null || file.getFileSize() <= 0L) {
            throw invalid("Voice audio file is empty.");
        }
        if (file.getFileSize() > effectiveMaxBytes) {
            throw invalid("Voice audio file exceeds the size limit.");
        }
        return serverMimeType;
    }

    static void validateDuration(Long audioDurationMs, Duration maxAudioDuration) {
        long maxDurationMs = maxAudioDuration == null
                ? Duration.ofMinutes(2).toMillis()
                : Math.max(1L, maxAudioDuration.toMillis());
        if (audioDurationMs == null || audioDurationMs <= 0L) {
            throw invalid("Voice audio duration is required.");
        }
        if (audioDurationMs > maxDurationMs) {
            throw invalid("Voice audio duration exceeds the recording limit.");
        }
    }

    private static void validateFilenameExtension(String filename, String expectedExtension) {
        if (!StringUtils.hasText(filename)) {
            return;
        }
        String normalized = filename.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
        int dot = basename.lastIndexOf('.');
        if (dot < 0 || dot == basename.length() - 1
                || !expectedExtension.equals(basename.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            throw invalid("Voice audio filename does not match server metadata.");
        }
    }

    private static String normalizeExtension(String value) {
        if (!StringUtils.hasText(value)) {
            throw invalid("Voice audio extension is missing.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMimeType(String value) {
        if (!StringUtils.hasText(value)) {
            throw invalid("Voice audio MIME type is missing.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        return semicolon < 0 ? normalized : normalized.substring(0, semicolon).trim();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR, message);
    }
}
