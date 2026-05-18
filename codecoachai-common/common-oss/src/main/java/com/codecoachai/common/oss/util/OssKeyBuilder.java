package com.codecoachai.common.oss.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * OSS Key 生成工具：统一目录规范。
 *
 * 目录约定：
 *   resume/{userId}/{yyyy}/{MM}/{uuid}.{ext}          原始简历
 *   resume-parsed/{resumeId}.json                       简历解析中间结果
 *   avatar/{userId}.{ext}                                头像
 *   report/{sessionId}.pdf                               面试报告导出
 *   question-import/{batchId}.{ext}                      题目批量导入
 *   attachment/{module}/{bizId}/{filename}               通用附件
 *   tmp/{uuid}.{ext}                                     临时文件（7 天自动清理）
 */
public final class OssKeyBuilder {

    private static final DateTimeFormatter YYYY = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MM = DateTimeFormatter.ofPattern("MM");

    private OssKeyBuilder() {}

    public static String resume(Long userId, String originalFilename) {
        LocalDateTime now = LocalDateTime.now();
        return "resume/" + userId + "/"
                + now.format(YYYY) + "/" + now.format(MM) + "/"
                + UUID.randomUUID().toString().replace("-", "") + extOf(originalFilename);
    }

    public static String resumeParsed(Long resumeId) {
        return "resume-parsed/" + resumeId + ".json";
    }

    public static String avatar(Long userId, String originalFilename) {
        return "avatar/" + userId + extOf(originalFilename);
    }

    public static String interviewReport(Long sessionId) {
        return "report/" + sessionId + ".pdf";
    }

    public static String questionImport(String batchId, String originalFilename) {
        return "question-import/" + batchId + extOf(originalFilename);
    }

    public static String attachment(String module, String bizId, String filename) {
        String safe = StringUtils.hasText(filename) ? filename : UUID.randomUUID().toString();
        return "attachment/" + module + "/" + bizId + "/" + safe;
    }

    public static String tmp(String originalFilename) {
        return "tmp/" + UUID.randomUUID().toString().replace("-", "") + extOf(originalFilename);
    }

    /**
     * 目录前缀（用于 STS Policy）：上层目录，末尾保留 /
     */
    public static String resumeDir(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return "resume/" + userId + "/" + now.format(YYYY) + "/" + now.format(MM) + "/";
    }

    private static String extOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx).toLowerCase() : "";
    }
}
