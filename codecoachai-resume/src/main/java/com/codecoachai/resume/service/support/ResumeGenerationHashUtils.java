package com.codecoachai.resume.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ResumeGenerationHashUtils {

    private ResumeGenerationHashUtils() {
    }

    public static String sha256(ObjectMapper objectMapper, Object value) {
        try {
            byte[] canonical = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("生成输入快照哈希失败", ex);
        }
    }
}
