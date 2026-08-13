package com.codecoachai.interview.feign.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InnerFileInfoVO {

    private Long id;
    private Long userId;
    private String bizType;
    private String originalFilename;
    private String storedFilename;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private String storageProvider;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
