package com.codecoachai.resume.service.support;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ResumeImportRepairSnapshotCipher {

    private static final String VERSION = "v1";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int REQUIRED_KEY_LENGTH_BYTES = 32;

    private final String configuredKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ResumeImportRepairSnapshotCipher(
            @Value("${RESUME_IMPORT_REPAIR_AUDIT_KEY:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法加密简历导入修复审计快照");
        }
    }

    public String decrypt(String ciphertext) {
        if (!StringUtils.hasText(ciphertext)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少可恢复的简历导入修复审计快照");
        }
        String[] parts = ciphertext.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历导入修复审计快照格式不受支持");
        }
        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历导入修复审计快照无法解密");
        }
    }

    private javax.crypto.SecretKey key() {
        if (!StringUtils.hasText(configuredKey)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "执行或回滚简历导入修复前必须配置 RESUME_IMPORT_REPAIR_AUDIT_KEY");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(configuredKey.trim());
            if (keyBytes.length != REQUIRED_KEY_LENGTH_BYTES) {
                throw invalidKey();
            }
            return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException ex) {
            throw invalidKey();
        }
    }

    private BusinessException invalidKey() {
        return new BusinessException(ErrorCode.PARAM_ERROR,
                "RESUME_IMPORT_REPAIR_AUDIT_KEY 必须是 32 字节 AES 密钥的 Base64 编码");
    }
}
