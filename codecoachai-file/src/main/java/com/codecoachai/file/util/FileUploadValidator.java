package com.codecoachai.file.util;

import static java.util.Map.entry;

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
    private static final Map<String, Set<String>> ALLOWED_MIME_TYPES = Map.ofEntries(
            entry("pdf", Set.of("application/pdf")),
            entry("doc", Set.of("application/msword")),
            entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/zip")),
            entry("md", Set.of("text/markdown", "text/plain")),
            entry("txt", Set.of("text/plain")),
            entry("webm", Set.of("audio/webm", "video/webm")),
            entry("wav", Set.of("audio/wav", "audio/wave", "audio/x-wav")),
            entry("mp3", Set.of("audio/mpeg", "audio/mp3")),
            entry("m4a", Set.of("audio/mp4", "audio/x-m4a", "audio/m4a")),
            entry("ogg", Set.of("audio/ogg", "application/ogg")),
            entry("jpg", Set.of("image/jpeg")),
            entry("jpeg", Set.of("image/jpeg")),
            entry("png", Set.of("image/png"))
    );

    private FileUploadValidator() {
    }

    public static void validateContent(MultipartFile file, String bizType, String fileExt) {
        String ext = normalizeExt(fileExt);
        validateMimeType(file.getContentType(), ext, FileBizTypes.isInterviewVoice(bizType));
        validateFileHeader(file, ext);
    }

    private static void validateMimeType(String contentType, String fileExt, boolean explicitMimeRequired) {
        if (!StringUtils.hasText(contentType)) {
            if (explicitMimeRequired) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice audio MIME type is required.");
            }
            return;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        if (GENERIC_MIME_TYPES.contains(normalized)) {
            if (explicitMimeRequired) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice audio MIME type must be explicit.");
            }
            return;
        }
        Set<String> allowed = ALLOWED_MIME_TYPES.get(fileExt);
        if (allowed != null && !allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "File MIME type does not match its extension.");
        }
    }

    private static void validateFileHeader(MultipartFile file, String fileExt) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(PROBE_BYTES);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "File content could not be read.");
        }
        if (header.length == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "File content is empty.");
        }

        boolean valid = switch (fileExt) {
            case "pdf" -> startsWith(header, "%PDF".getBytes(StandardCharsets.US_ASCII));
            case "doc" -> startsWith(header, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});
            case "docx" -> startsWith(header, new byte[] {0x50, 0x4B, 0x03, 0x04})
                    || startsWith(header, new byte[] {0x50, 0x4B, 0x05, 0x06})
                    || startsWith(header, new byte[] {0x50, 0x4B, 0x07, 0x08});
            case "md", "txt" -> looksLikeText(header);
            case "webm" -> startsWith(header, new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
            case "wav" -> startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII));
            case "mp3" -> startsWith(header, "ID3".getBytes(StandardCharsets.US_ASCII)) || looksLikeMp3Frame(header);
            case "m4a" -> hasFtypBox(header);
            case "ogg" -> startsWith(header, "OggS".getBytes(StandardCharsets.US_ASCII));
            case "jpg", "jpeg" -> startsWith(header, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "png" -> startsWith(header, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "File content does not match its extension.");
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
            if (unsigned == 0 || unsigned < 0x09) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeMp3Frame(byte[] bytes) {
        return bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF
                && ((bytes[1] & 0xE0) == 0xE0);
    }

    private static boolean hasFtypBox(byte[] bytes) {
        return bytes.length >= 8
                && bytes[4] == 'f'
                && bytes[5] == 't'
                && bytes[6] == 'y'
                && bytes[7] == 'p';
    }

    private static String normalizeExt(String fileExt) {
        if (!StringUtils.hasText(fileExt)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "File extension is required.");
        }
        return fileExt.toLowerCase(Locale.ROOT);
    }
}
