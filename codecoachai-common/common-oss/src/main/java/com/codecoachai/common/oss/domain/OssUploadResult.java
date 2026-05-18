package com.codecoachai.common.oss.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OSS 上传结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OssUploadResult {

    /** OSS 完整 Key（含目录前缀） */
    private String ossKey;

    /** OSS 公开访问域名 + Key（若 bucket 为私有，则该 URL 不可直接访问） */
    private String url;

    /** 文件字节数 */
    private Long size;

    /** ETag（OSS 返回） */
    private String etag;

    /** MD5（计算自上传字节流） */
    private String md5;

    /** MIME 类型 */
    private String contentType;
}
