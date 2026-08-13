package com.codecoachai.file.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_info")
public class FileInfo extends BaseEntity {

    private Long userId;
    private String bizType;
    private String originalFilename;
    private String storedFilename;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    /** 本地相对路径或 OSS Key 二选一存（保留兼容） */
    private String storagePath;
    /** OSS Key（V3 新增；V3_006 SQL 加列） */
    private String ossKey;
    /** OSS Bucket（V3 新增） */
    private String bucket;
    /** OSS ETag */
    private String etag;
    /** 文件指纹 MD5 */
    private String md5;
    /** 存储提供方：LOCAL / ALIYUN_OSS */
    private String storageProvider;
    private String status;
}
