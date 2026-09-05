package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobApplicationAttachmentVO {

    private Long id;
    private Long packageId;
    private Long applicationId;
    private Long fileId;
    private String attachmentType;
    private String displayName;
    private String originalFilename;
    private String mimeType;
    private Long fileSize;
    private Integer sortOrder;
    private String downloadUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
