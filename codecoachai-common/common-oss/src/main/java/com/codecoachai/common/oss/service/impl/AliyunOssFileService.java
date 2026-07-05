package com.codecoachai.common.oss.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.oss.config.OssProperties;
import com.codecoachai.common.oss.domain.OssUploadResult;
import com.codecoachai.common.oss.service.OssFileService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 阿里云 OSS 实现。
 */
@Slf4j
@RequiredArgsConstructor
public class AliyunOssFileService implements OssFileService {

    private final OSS ossClient;
    private final OssProperties properties;

    @Override
    public OssUploadResult upload(String ossKey, InputStream inputStream, long size, String contentType) {
        String key = applyPrefix(ossKey);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            if (size > 0) {
                metadata.setContentLength(size);
            }
            if (StringUtils.hasText(contentType)) {
                metadata.setContentType(contentType);
            }
            PutObjectRequest request = new PutObjectRequest(properties.getBucket(), key, inputStream, metadata);
            PutObjectResult result = ossClient.putObject(request);
            log.info("OSS 上传成功 key={} etag={}", key, result.getETag());
            return OssUploadResult.builder()
                    .ossKey(key)
                    .url(publicUrl(key))
                    .size(size > 0 ? size : null)
                    .etag(result.getETag())
                    .contentType(contentType)
                    .build();
        } catch (OSSException ex) {
            log.error("OSS 上传失败 key={} code={} msg={}", key, ex.getErrorCode(), ex.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败：" + ex.getErrorCode());
        }
    }

    @Override
    public OssUploadResult upload(String ossKey, byte[] bytes, String contentType) {
        String md5 = md5Base64(bytes);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            OssUploadResult result = upload(ossKey, bais, bytes.length, contentType);
            result.setMd5(md5);
            return result;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件流读取失败");
        }
    }

    @Override
    public byte[] download(String ossKey) {
        String key = applyPrefix(ossKey);
        try (InputStream is = ossClient.getObject(properties.getBucket(), key).getObjectContent()) {
            return is.readAllBytes();
        } catch (Exception ex) {
            log.error("OSS 下载失败 key={}", key, ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件下载失败");
        }
    }

    @Override
    public InputStream openStream(String ossKey) {
        String key = applyPrefix(ossKey);
        try {
            return ossClient.getObject(properties.getBucket(), key).getObjectContent();
        } catch (Exception ex) {
            log.error("OSS stream open failed key={}", key, ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS file stream open failed");
        }
    }

    @Override
    public void delete(String ossKey) {
        ossClient.deleteObject(properties.getBucket(), applyPrefix(ossKey));
    }

    @Override
    public boolean exists(String ossKey) {
        return ossClient.doesObjectExist(properties.getBucket(), applyPrefix(ossKey));
    }

    @Override
    public String signUrl(String ossKey, Duration expire) {
        String key = applyPrefix(ossKey);
        Duration effective = expire != null && !expire.isZero() && !expire.isNegative()
                ? expire
                : properties.signUrlExpire();
        Date expiration = new Date(System.currentTimeMillis() + effective.toMillis());
        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(properties.getBucket(), key);
        req.setExpiration(expiration);
        URL url = ossClient.generatePresignedUrl(req);
        return url.toString();
    }

    @Override
    public String publicUrl(String ossKey) {
        String key = applyPrefix(ossKey);
        if (StringUtils.hasText(properties.getDomain())) {
            String domain = properties.getDomain().endsWith("/")
                    ? properties.getDomain().substring(0, properties.getDomain().length() - 1)
                    : properties.getDomain();
            return domain + "/" + key;
        }
        return "https://" + properties.getBucket() + "." + properties.getEndpoint() + "/" + key;
    }

    /**
     * 自动应用 keyPrefix（如多环境隔离 dev/ prod/）。
     * 如果传入的 ossKey 已经以 prefix 开头，则不重复添加。
     */
    private String applyPrefix(String ossKey) {
        if (!StringUtils.hasText(properties.getKeyPrefix())) {
            return ossKey;
        }
        String prefix = properties.getKeyPrefix().endsWith("/")
                ? properties.getKeyPrefix()
                : properties.getKeyPrefix() + "/";
        return ossKey.startsWith(prefix) ? ossKey : prefix + ossKey;
    }

    private String md5Base64(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return Base64.getEncoder().encodeToString(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
