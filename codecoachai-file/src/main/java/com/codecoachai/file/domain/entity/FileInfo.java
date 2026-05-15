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
    private String storagePath;
    private String storageProvider;
    private String status;
}
