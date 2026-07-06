package com.codecoachai.common.oss.service;

import com.codecoachai.common.oss.domain.OssUploadResult;
import java.io.InputStream;
import java.time.Duration;

/**
 * OSS 文件服务统一接口。
 */
public interface OssFileService {

    /**
     * 上传字节流。
     *
     * @param ossKey      OSS Key（不含 bucket，相对路径）
     * @param inputStream 数据流
     * @param size        字节数（-1 表示未知，将转 byte[] 估算）
     * @param contentType MIME 类型
     */
    OssUploadResult upload(String ossKey, InputStream inputStream, long size, String contentType);

    /**
     * 上传字节数组（小文件便捷方法）。
     */
    OssUploadResult upload(String ossKey, byte[] bytes, String contentType);

    /**
     * 下载到字节数组（小文件用）。
     */
    byte[] download(String ossKey);

    InputStream openStream(String ossKey);

    /**
     * 删除单个对象。
     */
    void delete(String ossKey);

    /**
     * 判断对象是否存在。
     */
    boolean exists(String ossKey);

    /**
     * 生成带签名的访问 URL（用于私有 bucket 的临时访问）。
     */
    String signUrl(String ossKey, Duration expire);

    /**
     * 生成完整 URL（公开 bucket 或带 CDN 域名）。
     */
    String publicUrl(String ossKey);
}
