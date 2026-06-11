package com.codecoachai.file.util;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public final class FileUploadValidator {

    private static final int PROBE_BYTES = 16;
    private static final Set<String> GENERIC_MIME_TYPES = Set.of(
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            "binary/octet-stream"
    );
    private static final Map<String, Set<String>> ALLOWED_MIME_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "doc", Set.of("application/msword"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/zip"),
            "md", Set.of("text/markdown", "text/plain"),
            "txt", Set.of("text/plain")
    );

    private FileUploadValidator() {
    }

    public static void validateContent(MultipartFile file, String fileExt) {
        String ext = normalizeExt(fileExt);
        validateMimeType(file.getContentType(), ext);
        validateFileHeader(file, ext);
    }

    private static void validateMimeType(String contentType, String fileExt) {
        if (!StringUtils.hasText(contentType)) {
            return;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        if (GENERIC_MIME_TYPES.contains(normalized)) {
            return;
        }
        Set<String> allowed = ALLOWED_MIME_TYPES.get(fileExt);
        if (allowed != null && !allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件类型不支持，请上传 PDF、Word 或文本文件。");
        }
    }

    private static void validateFileHeader(MultipartFile file, String fileExt) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(PROBE_BYTES);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件内容读取失败，请重新选择文件后再试。");
        }
        if (header.length == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件为空，请上传有效文件。");
        }

        boolean valid = switch (fileExt) {
            case "pdf" -> startsWith(header, "%PDF".getBytes(StandardCharsets.US_ASCII));
            case "doc" -> startsWith(header, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});
            case "docx" -> startsWith(header, new byte[] {0x50, 0x4B, 0x03, 0x04})
                    || startsWith(header, new byte[] {0x50, 0x4B, 0x05, 0x06})
                    || startsWith(header, new byte[] {0x50, 0x4B, 0x07, 0x08});
            case "md", "txt" -> looksLikeText(header);
            default -> true;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件内容与扩展名不匹配，请确认文件格式后重新上传。");
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeText(byte[] bytes) {
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            if (unsigned == 0) {
                return false;
            }
            if (unsigned < 0x09) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeExt(String fileExt) {
        if (!StringUtils.hasText(fileExt)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件扩展名不能为空。");
        }
        return fileExt.toLowerCase(Locale.ROOT);
    }
}
