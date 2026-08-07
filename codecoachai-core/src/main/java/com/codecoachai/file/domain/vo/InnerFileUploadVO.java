package com.codecoachai.file.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InnerFileUploadVO {

    private Long fileId;
    private Long userId;
    private String bizType;
    private String originalFilename;
    private String storedFilename;
    private Long fileSize;
    private String fileExt;
    private String mimeType;
    private String storagePath;
    private String storageProvider;
    private String status;
    private LocalDateTime createdAt;
}
