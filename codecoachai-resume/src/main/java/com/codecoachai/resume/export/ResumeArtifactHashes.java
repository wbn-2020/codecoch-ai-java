package com.codecoachai.resume.export;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ResumeArtifactHashes {

    private ResumeArtifactHashes() {
    }

    public static String sha256(String value) {
        MessageDigest digest = digest();
        digest.update(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String sha256(byte[] value) {
        MessageDigest digest = digest();
        digest.update(value == null ? new byte[0] : value);
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
