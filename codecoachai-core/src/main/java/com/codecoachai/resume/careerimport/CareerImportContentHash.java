package com.codecoachai.resume.careerimport;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.util.StringUtils;

final class CareerImportContentHash {

    private CareerImportContentHash() {
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    static String verifyClaim(byte[] content, String claimedHash, String fieldName) {
        String actualHash = sha256(content);
        if (!StringUtils.hasText(claimedHash)) {
            return actualHash;
        }
        String normalized = claimedHash.trim().toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + " must be a SHA-256 hash");
        }
        if (!MessageDigest.isEqual(
                actualHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                normalized.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Uploaded file content hash mismatch");
        }
        return actualHash;
    }

    static void verifyPreview(String actualHash, String previewHash) {
        if (!StringUtils.hasText(previewHash)) {
            return;
        }
        String normalized = previewHash.trim().toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}") || !actualHash.equals(normalized)) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "Selected file changed after preview; preview the current file again");
        }
    }
}
