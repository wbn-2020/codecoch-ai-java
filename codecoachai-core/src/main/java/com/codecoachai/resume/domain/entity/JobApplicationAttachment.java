package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_application_attachment")
public class JobApplicationAttachment extends BaseEntity {

    private Long userId;
    private Long packageId;
    private Long applicationId;
    private Long fileId;
    private String attachmentType;
    private String displayName;
    private String originalFilename;
    private String mimeType;
    private Long fileSize;
    private Integer sortOrder;
}
