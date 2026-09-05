package com.codecoachai.file.util;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.file.domain.entity.FileInfo;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

public final class FileDownloadValidator {

    private FileDownloadValidator() {
    }

    public static MediaType validate(FileInfo fileInfo, Long actualSize) {
        if (fileInfo == null
                || !StringUtils.hasText(fileInfo.getOriginalFilename())
                || !StringUtils.hasText(fileInfo.getFileExt())
                || fileInfo.getFileSize() == null
                || fileInfo.getFileSize() <= 0) {
            throw unavailable();
        }
        String expectedExt = fileInfo.getFileExt().trim().toLowerCase(Locale.ROOT);
        String filename = fileInfo.getOriginalFilename().replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        int extensionIndex = filename.lastIndexOf('.');
        String actualExt = extensionIndex < 0 || extensionIndex == filename.length() - 1
                ? ""
                : filename.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        if (!expectedExt.equals(actualExt)) {
            throw unavailable();
        }
        if (actualSize != null && !fileInfo.getFileSize().equals(actualSize)) {
            throw unavailable();
        }
        if (!StringUtils.hasText(fileInfo.getMimeType())) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(fileInfo.getMimeType());
            if (!FileUploadValidator.isMimeCompatible(mediaType.toString(), expectedExt)) {
                throw unavailable();
            }
            return mediaType;
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(
                ErrorCode.PARAM_ERROR,
                "File metadata, size, or type is invalid for download.");
    }
}
