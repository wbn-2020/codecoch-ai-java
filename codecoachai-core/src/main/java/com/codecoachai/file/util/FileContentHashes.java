package com.codecoachai.file.util;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.web.multipart.MultipartFile;

public final class FileContentHashes {

    private static final int BUFFER_BYTES = 8192;

    private FileContentHashes() {
    }

    public static String sha256(MultipartFile file) {
        if (file == null) {
            throw unreadable();
        }
        try (InputStream input = file.getInputStream()) {
            return sha256(input);
        } catch (IOException ex) {
            throw unreadable();
        }
    }

    public static String sha256(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return sha256(input);
        } catch (IOException ex) {
            throw unreadable();
        }
    }

    public static String sha256(InputStream input) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static BusinessException unreadable() {
        return new BusinessException(ErrorCode.PARAM_ERROR, "File content could not be read.");
    }
}
